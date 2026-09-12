package com.tutorspoint.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** The six-digit code texted to the account's phone number (FR-A2). */
public record VerifyOtpRequest(

        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        String email,

        @NotBlank(message = "{validation.otp.required}")
        @Pattern(regexp = AuthValidation.OTP_PATTERN, message = "{validation.otp.invalid}")
        String code) {
}
