package com.tutorspoint.admin.dto;

import jakarta.validation.constraints.NotNull;

/** Granting or withdrawing a tutor's verified badge (FR-R3). */
public record VerificationRequest(
        @NotNull(message = "{validation.admin.verified.required}")
        Boolean verified) {
}
