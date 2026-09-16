package com.tutorspoint.admin.dto;

import com.tutorspoint.auth.domain.AccountStatus;
import com.tutorspoint.auth.domain.Role;

import java.time.Instant;

/**
 * One account as a moderator sees it. More than a user sees of anybody else - the email and
 * phone number are how an administrator reaches a person during a pilot - and still nothing
 * internal: no password hash, no token state.
 */
public record AdminUserSummary(
        Long id,
        String fullName,
        String email,
        String phoneNumber,
        Role role,
        AccountStatus status,
        boolean emailVerified,
        boolean phoneVerified,
        Instant registeredAt,
        Instant lastLoginAt) {
}
