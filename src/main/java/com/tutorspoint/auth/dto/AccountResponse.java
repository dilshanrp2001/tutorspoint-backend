package com.tutorspoint.auth.dto;

import com.tutorspoint.auth.domain.AccountStatus;
import com.tutorspoint.auth.domain.Role;
import com.tutorspoint.common.domain.Language;

import java.time.Instant;

/**
 * An account as its owner sees it (FR-A7), and the body registration returns so the client
 * knows what still needs verifying.
 *
 * <p>Carries no password hash and no verification secrets. Only the owner ever receives one:
 * what the public sees of a tutor is a different, published shape in Phase 2.
 */
public record AccountResponse(
        Long id,
        String email,
        String fullName,
        String phoneNumber,
        Role role,
        boolean emailVerified,
        boolean phoneVerified,
        Language preferredLanguage,
        AccountStatus status,
        Instant lastLoginAt) {
}
