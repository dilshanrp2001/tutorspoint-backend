package com.tutorspoint.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Credentials for sign-in (FR-A4).
 *
 * <p>No length or strength rules on the password: this is not where a password is chosen,
 * and rejecting a wrong-shaped one differently from a wrong one would describe the password
 * policy to an attacker instead of answering about this account.
 */
public record LoginRequest(

        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        String email,

        @NotBlank(message = "{validation.password.required}")
        String password) {
}
