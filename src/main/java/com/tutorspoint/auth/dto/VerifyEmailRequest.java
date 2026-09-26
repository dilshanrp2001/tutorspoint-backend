package com.tutorspoint.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** The token from the emailed verification link (FR-A3). */
public record VerifyEmailRequest(

        @NotBlank(message = "{validation.token.required}")
        String token) {
}
