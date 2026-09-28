package com.tutorspoint.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * The single envelope every endpoint returns, so the frontend can rely on one shape.
 * Exactly one of {@code data} or {@code error} is populated.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error,
        Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> failure(ApiError error) {
        return new ApiResponse<>(false, null, error, Instant.now());
    }

    /**
     * Error detail. {@code code} is a stable machine-readable key the client can switch
     * on and translate; {@code message} is human-readable and must never carry
     * internals such as stack traces or SQL.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiError(
            String code,
            String message,
            Map<String, String> fieldErrors) {

        public static ApiError of(String code, String message) {
            return new ApiError(code, message, null);
        }

        public static ApiError withFields(String code, String message, Map<String, String> fieldErrors) {
            return new ApiError(code, message, fieldErrors);
        }
    }
}
