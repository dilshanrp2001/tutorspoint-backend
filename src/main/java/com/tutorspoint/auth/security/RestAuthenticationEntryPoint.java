package com.tutorspoint.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Answers an unauthenticated request to a protected route with the standard error
 * envelope, instead of the HTML login redirect Spring Security would default to.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String CODE = "NOT_AUTHENTICATED";

    private final ApiErrorResponder errorResponder;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) {
        log.debug("Unauthenticated request to {} {}", request.getMethod(), request.getRequestURI());
        errorResponder.write(response, HttpStatus.UNAUTHORIZED, CODE,
                "This endpoint requires a signed-in user");
    }
}
