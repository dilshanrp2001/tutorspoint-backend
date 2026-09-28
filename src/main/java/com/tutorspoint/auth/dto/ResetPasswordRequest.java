package com.tutorspoint.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Finishes password recovery: the emailed token plus the new password (FR-A6). */
public record ResetPasswordRequest(

        @NotBlank(message = "{validation.token.required}")
        String token,

        @NotBlank(message = "{validation.password.required}")
        @Size(min = AuthValidation.PASSWORD_MIN_LENGTH,
                max = AuthValidation.PASSWORD_MAX_LENGTH,
                message = "{validation.password.size}")
        @Pattern(regexp = AuthValidation.PASSWORD_PATTERN, message = "{validation.password.weak}")
        String newPassword) {
}
