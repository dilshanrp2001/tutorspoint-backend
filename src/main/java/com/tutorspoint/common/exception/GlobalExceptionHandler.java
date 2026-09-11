package com.tutorspoint.common.exception;

import com.tutorspoint.common.api.ApiResponse;
import com.tutorspoint.common.api.ApiResponse.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
