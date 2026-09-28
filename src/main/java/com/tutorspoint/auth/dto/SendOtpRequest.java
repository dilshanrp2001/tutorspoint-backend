package com.tutorspoint.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Asks for a fresh phone code (FR-A2). Identified by email rather than by phone number: the
 * code belongs to an account, and the number is read off that account so nobody can have a
 * code sent to a number they do not own.
 */
public record SendOtpRequest(

        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        String email) {
}
