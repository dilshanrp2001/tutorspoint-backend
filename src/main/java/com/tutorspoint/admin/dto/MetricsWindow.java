package com.tutorspoint.admin.dto;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * The days a metrics summary covers, both inclusive, in Sri Lanka time.
 *
 * @param from the first day, or null to count from the beginning
 * @param to   the last day, or null for today
 */
public record MetricsWindow(
        @Parameter(description = "First day counted, inclusive (yyyy-MM-dd). Omit to count from the start.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate from,

        @Parameter(description = "Last day counted, inclusive (yyyy-MM-dd). Omit for today.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate to) {

    @AssertTrue(message = "{validation.admin.metrics.range}")
    private boolean isOrdered() {
        return from == null || to == null || !from.isAfter(to);
    }
}
