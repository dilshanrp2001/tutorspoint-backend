package com.tutorspoint.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Carries the refresh token for the rotate and logout endpoints (FR-A4). */
public record RefreshTokenRequest(

        @NotBlank(message = "{validation.refresh-token.required}")
        String refreshToken) {
}
