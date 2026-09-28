package com.tutorspoint.common.exception;

import com.tutorspoint.common.api.ApiResponse;
import com.tutorspoint.common.logging.RequestIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearLoggingContext() {
        MDC.clear();
    }

    @Test
    void aConstraintLostToAConcurrentRequestIsAConflictThatNamesNoRow() {
        DataIntegrityViolationException raced = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_users_phone\": Key (phone_number)=(+94771234567)");

        ResponseEntity<ApiResponse<Void>> response = handler.handleConstraintViolation(raced);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error().code()).isEqualTo("CONFLICT");
        assertThat(response.getBody().error().message()).doesNotContain("94771234567", "uq_users_phone");
    }

    @Test
    void anUnexpectedErrorQuotesTheRequestIdSoTheLogCanBeFound() {
        MDC.put(RequestIdFilter.MDC_KEY, "req-0f8c2b9e");

        ResponseEntity<ApiResponse<Void>> response = handler.handleUnexpected(new IllegalStateException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error().message()).contains("req-0f8c2b9e").doesNotContain("boom");
    }
}
