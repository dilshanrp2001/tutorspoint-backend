package com.tutorspoint.auth.dto;

import com.tutorspoint.common.domain.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The details a user may edit on their own account (FR-A7).
 *
 * <p>Email and phone number are not among them. Both are verified identifiers and both are
 * unique keys, so changing either is a re-verification flow — issue a new token or code,
 * clear the verified flag, confirm — not a field edit. Accepting them here would let an
 * account sit ACTIVE with an unverified contact address.
 */
public record UpdateAccountRequest(

        @NotBlank(message = "{validation.full-name.required}")
        @Size(max = 150, message = "{validation.full-name.size}")
        String fullName,

        @NotNull(message = "{validation.language.required}")
        Language preferredLanguage) {
}
