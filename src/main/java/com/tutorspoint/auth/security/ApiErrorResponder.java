package com.tutorspoint.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorspoint.common.api.ApiResponse;
import com.tutorspoint.common.api.ApiResponse.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes the standard error envelope from inside the security filter chain.
 *
 * <p>The global {@code @RestControllerAdvice} cannot help here: these failures happen
 * before a controller is reached, so Spring Security would answer with its own body and
 * the client would face two different error shapes depending on how far the request got.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiErrorResponder {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, HttpStatus status, String code, String message) {
        if (response.isCommitted()) {
            log.warn("Cannot write {} error {}: response already committed", status, code);
            return;
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            objectMapper.writeValue(response.getOutputStream(),
                    ApiResponse.failure(ApiError.of(code, message)));
        } catch (IOException e) {
            // The client is gone. Nothing left to tell them; do not mask it as a 500.
            log.warn("Could not write {} error {} to the response", status, code, e);
        }
    }
}
