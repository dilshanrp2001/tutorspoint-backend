package com.tutorspoint.common.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorspoint.auth.security.ApiErrorResponder;
import com.tutorspoint.auth.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Answers 429 once a client, or an account, has used up its allowance for one of the
 * {@link RateLimitedAction}s.
 *
 * <p>Two buckets per action, checked in this order:
 *
 * <ul>
 *   <li><strong>Per client address</strong>, for everyone. Stops one machine running a script.</li>
 *   <li><strong>Per account</strong>, where there is one to key on: the signed-in user for enquiries
 *       and search, and the email address in the body for sign-in, registration, OTP and reset
 *       requests. Stops a script spread across many addresses from concentrating on one
 *       account - guessing its password, or burning its SMS budget.</li>
 * </ul>
 *
 * <p>These sit in front of the domain caps, not instead of them: the enquiry service still
 * counts a parent's enquiries in the database, and the OTP cap still counts sends. Those are
 * rules about accounts that hold however many instances run; this is load shedding at the edge.
 *
 * <p>The client address is {@code getRemoteAddr()}, for the reason {@code ServletRequestOrigin}
 * gives: a forwarded-for header is whatever the caller wrote. Behind the Phase 5.4 proxy that
 * address is the proxy's until {@code server.forward-headers-strategy} is set - which must happen
 * there, or every visitor will share one bucket.
 *
 * <p>Runs inside the security chain after the JWT filter, so the signed-in user is known.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    static final String CODE = "RATE_LIMITED";

    /**
     * How much of a body is read to find the email address. Every body this applies to is a
     * few fields of JSON; a larger one is passed on untouched and keyed by address alone, so a
     * client cannot make this filter buffer an arbitrary amount of memory.
     */
    private static final int MAX_INSPECTED_BODY_BYTES = 8 * 1024;
    private static final int MAX_EMAIL_LENGTH = 254;

    private static final List<Route> ROUTES = List.of(
            new Route(HttpMethod.POST, "/api/auth/login", RateLimitedAction.LOGIN, AccountKey.EMAIL_IN_BODY),
            new Route(HttpMethod.POST, "/api/auth/register", RateLimitedAction.REGISTER, AccountKey.EMAIL_IN_BODY),
            new Route(HttpMethod.POST, "/api/auth/request-otp", RateLimitedAction.OTP_REQUEST, AccountKey.EMAIL_IN_BODY),
            new Route(HttpMethod.POST, "/api/auth/forgot-password", RateLimitedAction.PASSWORD_RESET, AccountKey.EMAIL_IN_BODY),
            // The body holds a reset token, which is a secret and never a bucket key.
            new Route(HttpMethod.POST, "/api/auth/reset-password", RateLimitedAction.PASSWORD_RESET, AccountKey.NONE),
            new Route(HttpMethod.POST, "/api/enquiries", RateLimitedAction.ENQUIRY_CREATION, AccountKey.SIGNED_IN_USER),
            new Route(HttpMethod.GET, "/api/search/tutors", RateLimitedAction.SEARCH, AccountKey.SIGNED_IN_USER));

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final ApiErrorResponder errorResponder;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || routeFor(request).isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Route route = routeFor(request).orElseThrow();
        RateLimitProperties.ActionLimits limits = properties.limitsFor(route.action());

        Optional<Duration> wait = rateLimiter.tryAcquire(
                key(route.action(), "client", request.getRemoteAddr()), limits.perClient());
        if (wait.isPresent()) {
            reject(response, route.action(), "client address", wait.get());
            return;
        }

        HttpServletRequest forwarded = request;
        if (limits.perAccount() != null) {
            Optional<String> account = switch (route.accountKey()) {
                case NONE -> Optional.empty();
                case SIGNED_IN_USER -> signedInUserId().map(id -> "user:" + id);
                case EMAIL_IN_BODY -> {
                    InspectedRequest inspected = InspectedRequest.of(request);
                    forwarded = inspected;
                    yield emailIn(inspected.inspectedBytes()).map(email -> "email:" + email);
                }
            };
            if (account.isPresent()) {
                wait = rateLimiter.tryAcquire(key(route.action(), "account", account.get()), limits.perAccount());
                if (wait.isPresent()) {
                    reject(response, route.action(), "account", wait.get());
                    return;
                }
            }
        }
        chain.doFilter(forwarded, response);
    }

    private void reject(HttpServletResponse response, RateLimitedAction action, String scope, Duration wait) {
        // Neither the address nor the email is logged: the request id in the log context already
        // ties this line to the request, and an email address is not something a log needs.
        log.warn("Rate limit reached for {} per {}", action, scope);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds(wait)));
        errorResponder.write(response, HttpStatus.TOO_MANY_REQUESTS, CODE,
                "Too many requests. Wait before trying again; Retry-After gives the number of seconds.");
    }

    /** Whole seconds, rounded up and never zero: "retry after 0" invites an immediate retry. */
    static long retryAfterSeconds(Duration wait) {
        long seconds = wait.toSeconds() + (wait.toNanosPart() > 0 ? 1 : 0);
        return Math.max(1, seconds);
    }

    private Optional<String> emailIn(byte[] body) {
        if (body.length == 0 || body.length > MAX_INSPECTED_BODY_BYTES) {
            return Optional.empty();
        }
        try {
            JsonNode email = objectMapper.readTree(body).path("email");
            if (!email.isTextual()) {
                return Optional.empty();
            }
            String normalised = email.asText().trim().toLowerCase(Locale.ROOT);
            return normalised.isEmpty() || normalised.length() > MAX_EMAIL_LENGTH
                    ? Optional.empty()
                    : Optional.of(normalised);
        } catch (IOException malformed) {
            // Not this filter's to reject: validation answers a malformed body with a 400.
            return Optional.empty();
        }
    }

    private static Optional<Long> signedInUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                ? Optional.of(user.userId())
                : Optional.empty();
    }

    private static Optional<Route> routeFor(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return ROUTES.stream()
                .filter(route -> route.method().matches(request.getMethod()) && route.path().equals(path))
                .findFirst();
    }

    private static String key(RateLimitedAction action, String scope, String value) {
        return action + "|" + scope + "|" + value;
    }

    private enum AccountKey { NONE, SIGNED_IN_USER, EMAIL_IN_BODY }

    private record Route(HttpMethod method, String path, RateLimitedAction action, AccountKey accountKey) {
    }

    /**
     * A request whose first bytes have been read for inspection and are replayed, followed by
     * the rest of the original stream, to whatever reads the body next.
     */
    static final class InspectedRequest extends HttpServletRequestWrapper {

        private final byte[] head;
        private final InputStream body;

        private InspectedRequest(HttpServletRequest request, byte[] head, InputStream body) {
            super(request);
            this.head = head;
            this.body = body;
        }

        static InspectedRequest of(HttpServletRequest request) throws IOException {
            ServletInputStream original = request.getInputStream();
            // One byte past the limit, so an oversized body is recognisable as one.
            byte[] head = original.readNBytes(MAX_INSPECTED_BODY_BYTES + 1);
            return new InspectedRequest(request, head,
                    new SequenceInputStream(new ByteArrayInputStream(head), original));
        }

        byte[] inspectedBytes() {
            return head;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new ServletInputStream() {
                private boolean finished;

                @Override
                public int read() throws IOException {
                    int next = body.read();
                    finished = next == -1;
                    return next;
                }

                @Override
                public int read(byte[] buffer, int offset, int length) throws IOException {
                    int count = body.read(buffer, offset, length);
                    finished = count == -1;
                    return count;
                }

                @Override
                public boolean isFinished() {
                    return finished;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Asynchronous reads are not supported");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
