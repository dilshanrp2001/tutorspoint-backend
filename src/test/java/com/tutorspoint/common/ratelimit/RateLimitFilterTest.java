package com.tutorspoint.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorspoint.auth.domain.Role;
import com.tutorspoint.auth.security.ApiErrorResponder;
import com.tutorspoint.auth.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The filter against a recording limiter: which buckets a request is counted in, what reaches
 * the rest of the chain, and what a refusal looks like.
 */
class RateLimitFilterTest {

    private static final RateLimitProperties.Limit LIMIT = new RateLimitProperties.Limit(5, Duration.ofMinutes(1));

    private final List<String> keys = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String refuseKey;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        Map<RateLimitedAction, RateLimitProperties.ActionLimits> actions = new EnumMap<>(RateLimitedAction.class);
        Arrays.stream(RateLimitedAction.values())
                .forEach(action -> actions.put(action, new RateLimitProperties.ActionLimits(LIMIT, LIMIT)));
        RateLimiter recording = (key, limit) -> {
            keys.add(key);
            return key.equals(refuseKey) ? Optional.of(Duration.ofMillis(61_500)) : Optional.empty();
        };
        filter = new RateLimitFilter(recording, new RateLimitProperties(true, actions),
                new ApiErrorResponder(objectMapper), objectMapper);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void signInIsCountedPerAddressAndPerNormalisedEmailAndTheBodyIsPassedOnWhole() throws Exception {
        String body = "{\"email\":\"  Kamal@Example.LK \",\"password\":\"Colombo2026\"}";
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(post("/api/auth/login", body), new MockHttpServletResponse(), chain);

        assertThat(keys).containsExactly("LOGIN|client|10.0.0.7", "LOGIN|account|email:kamal@example.lk");
        HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
        assertThat(new String(forwarded.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(body);
    }

    @Test
    void aBodyTooLargeToInspectIsStillPassedOnWholeAndKeyedByAddressAlone() throws Exception {
        String body = "{\"email\":\"kamal@example.lk\",\"padding\":\"" + "x".repeat(20_000) + "\"}";
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(post("/api/auth/register", body), new MockHttpServletResponse(), chain);

        assertThat(keys).containsExactly("REGISTER|client|10.0.0.7");
        HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
        assertThat(new String(forwarded.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(body);
    }

    @Test
    void aResetTokenIsNeverUsedAsAKey() throws Exception {
        filter.doFilter(post("/api/auth/reset-password", "{\"token\":\"secret\",\"email\":\"x@y.lk\"}"),
                new MockHttpServletResponse(), new MockFilterChain());

        assertThat(keys).containsExactly("PASSWORD_RESET|client|10.0.0.7");
    }

    @Test
    void aSignedInSearchIsCountedAgainstTheAccount() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(42L, "kamal@example.lk", Role.PARENT);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.authorities()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/search/tutors");
        request.setRemoteAddr("10.0.0.7");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(keys).containsExactly("SEARCH|client|10.0.0.7", "SEARCH|account|user:42");
    }

    @Test
    void aGuestSearchIsCountedAgainstTheAddressOnly() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/search/tutors");
        request.setRemoteAddr("10.0.0.7");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(keys).containsExactly("SEARCH|client|10.0.0.7");
    }

    @Test
    void aRefusalIs429WithRetryAfterRoundedUpAndStopsTheChain() throws Exception {
        refuseKey = "LOGIN|client|10.0.0.7";
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(post("/api/auth/login", "{\"email\":\"a@b.lk\"}"), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("62");
        assertThat(response.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"");
        assertThat(chain.getRequest()).as("the request went no further").isNull();
        assertThat(keys).as("the account bucket is not spent once the address is refused").hasSize(1);
    }

    @Test
    void unlimitedRoutesAndDisabledLimitsAreNotCounted() throws Exception {
        MockHttpServletRequest other = new MockHttpServletRequest("GET", "/api/reference/subjects");
        filter.doFilter(other, new MockHttpServletResponse(), new MockFilterChain());
        MockHttpServletRequest wrongMethod = new MockHttpServletRequest("GET", "/api/auth/login");
        filter.doFilter(wrongMethod, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(keys).isEmpty();
    }

    @Test
    void retryAfterIsWholeSecondsNeverZero() {
        assertThat(RateLimitFilter.retryAfterSeconds(Duration.ofMillis(1))).isEqualTo(1);
        assertThat(RateLimitFilter.retryAfterSeconds(Duration.ofSeconds(30))).isEqualTo(30);
        assertThat(RateLimitFilter.retryAfterSeconds(Duration.ofMillis(30_001))).isEqualTo(31);
        assertThat(RateLimitFilter.retryAfterSeconds(Duration.ZERO)).isEqualTo(1);
    }

    private static MockHttpServletRequest post(String path, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("10.0.0.7");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
