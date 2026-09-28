package com.tutorspoint.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Answers a known caller who may not do this with the standard error envelope.
 *
 * <p>Logged at warn: a signed-in user reaching a route their role forbids is either a
 * frontend showing something it should not, or somebody probing. Both are worth seeing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final String CODE = "ACCESS_DENIED";

    private final ApiErrorResponder errorResponder;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) {
        log.warn("Access denied for {} {}", request.getMethod(), request.getRequestURI());
        errorResponder.write(response, HttpStatus.FORBIDDEN, CODE,
                "You are not allowed to perform this action");
    }
}
