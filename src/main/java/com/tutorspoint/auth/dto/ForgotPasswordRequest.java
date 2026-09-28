package com.tutorspoint.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Starts password recovery (FR-A6). The response is identical whether or not the address is
 * registered, so this endpoint can never be used to discover who holds an account.
 */
public record ForgotPasswordRequest(

        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        String email) {
}
