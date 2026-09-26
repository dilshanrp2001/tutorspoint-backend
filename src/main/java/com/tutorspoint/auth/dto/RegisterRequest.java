package com.tutorspoint.auth.dto;

import com.tutorspoint.common.domain.Language;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A new tutor, parent or student account (FR-A1). The role is chosen by the caller, in the body. */
public record RegisterRequest(

        @NotNull(message = "{validation.role.required}")
        RegistrableRole role,

        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        @Size(max = 254, message = "{validation.email.invalid}")
        String email,

        @NotBlank(message = "{validation.password.required}")
        @Size(min = AuthValidation.PASSWORD_MIN_LENGTH,
                max = AuthValidation.PASSWORD_MAX_LENGTH,
                message = "{validation.password.size}")
        @Pattern(regexp = AuthValidation.PASSWORD_PATTERN, message = "{validation.password.weak}")
        String password,

        @NotBlank(message = "{validation.full-name.required}")
        @Size(max = 150, message = "{validation.full-name.size}")
        String fullName,

        @NotBlank(message = "{validation.phone.required}")
        @Pattern(regexp = AuthValidation.PHONE_PATTERN, message = "{validation.phone.invalid}")
        String phoneNumber,

        @NotNull(message = "{validation.language.required}")
        Language preferredLanguage) {
}
