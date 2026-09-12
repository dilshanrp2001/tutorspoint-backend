package com.tutorspoint.common.exception;

import com.tutorspoint.common.api.ApiResponse;
import com.tutorspoint.common.api.ApiResponse.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The single place errors become HTTP responses. Controllers therefore contain
 * no try/catch and error formatting has one authoritative home.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        log.debug("Resource not found: {}", ex.getMessage());
        return respond(HttpStatus.NOT_FOUND, ApiError.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRule(BusinessRuleViolationException ex) {
        log.debug("Business rule violated: {}", ex.getMessage());
        return respond(HttpStatus.CONFLICT, ApiError.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationFailed(AuthenticationFailedException ex) {
        log.debug("Authentication failed: {}", ex.getMessage());
        return respond(HttpStatus.UNAUTHORIZED, ApiError.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedActionException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedAction(UnauthorizedActionException ex) {
        log.warn("Unauthorized action: {}", ex.getMessage());
        return respond(HttpStatus.FORBIDDEN, ApiError.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            String message = error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage();
            fieldErrors.merge(error.getField(), message, (first, second) -> first + "; " + second);
        }
        return respond(HttpStatus.BAD_REQUEST,
                ApiError.withFields("VALIDATION_FAILED", "Request validation failed", fieldErrors));
    }

    /**
     * The body could not be read at all: malformed JSON, or a value outside what the DTO can
     * hold — {@code "role": "ADMIN"} against an enum that offers only TUTOR and PARENT.
     *
     * <p>That is the caller's mistake, so it is a 400. Without this it would reach the
     * catch-all and be reported as a server error, which would send a client looking for a
     * fault that is in its own request. The cause is deliberately not echoed: it carries
     * internal type names and the offending input.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return respond(HttpStatus.BAD_REQUEST, ApiError.of("MALFORMED_REQUEST",
                "The request body could not be read. Check the JSON and the allowed values."));
    }

    /**
     * Raised by {@code @PreAuthorize} when a method-level rule rejects an authenticated
     * caller. Without this, Spring Security's own 403 page would bypass the standard
     * error envelope that every other failure returns.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return respond(HttpStatus.FORBIDDEN,
                ApiError.of("ACCESS_DENIED", "You are not allowed to perform this action"));
    }

    /**
     * Catch-all. The stack trace is logged server-side against a correlation id and
     * never reaches the client; the response carries only the id.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        String errorId = UUID.randomUUID().toString();
        log.error("Unhandled exception [errorId={}]", errorId, ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ApiError.of("INTERNAL_ERROR",
                "An unexpected error occurred. Quote reference " + errorId + " when reporting this."));
    }

    private ResponseEntity<ApiResponse<Void>> respond(HttpStatus status, ApiError error) {
        return ResponseEntity.status(status).body(ApiResponse.failure(error));
    }
}
