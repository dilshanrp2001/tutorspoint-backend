package com.tutorspoint.auth.security;

import com.tutorspoint.common.exception.AuthenticationFailedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Turns a {@code Bearer} access token into an authenticated request.
 *
 * <p>Stateless: the token is the whole session, so nothing is looked up and no session is
 * created. A request with no {@code Authorization} header passes straight through
 * unauthenticated — the route rules then decide whether that was allowed, which is what
 * keeps the public endpoints public.
 *
 * <p>A header that is present but unusable is rejected here and now, with the reason. The
 * alternative — dropping the bad token and carrying on anonymously — would surface as a
 * confusing 401 from the entry point, and the client could not tell "your token expired,
 * refresh it" from "this endpoint needs a login".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ApiErrorResponder errorResponder;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = bearerToken(request);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        AuthenticatedUser caller;
        try {
            caller = jwtService.parse(token);
        } catch (AuthenticationFailedException e) {
            SecurityContextHolder.clearContext();
            log.debug("Rejected access token on {} {}: {}",
                    request.getMethod(), request.getRequestURI(), e.getCode());
            errorResponder.write(response, HttpStatus.UNAUTHORIZED, e.getCode(), e.getMessage());
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(caller, null, caller.authorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
