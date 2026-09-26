package com.tutorspoint.auth.dto;

/**
 * The input shapes the auth endpoints accept, in one place because several DTOs enforce the
 * same rule and an annotation needs a compile-time constant.
 *
 * <p>Format rules only. Whether an email is already taken, whether a code is still live,
 * whether an account may sign in — none of that is knowable from a request body, and it
 * belongs in the service.
 */
public final class AuthValidation {

    /**
     * E.164 with a country code, e.g. +94771234567. Deliberately not Sri-Lanka-only: a
     * tutor may hold a foreign number, and the SMS gateway is addressed in E.164 anyway.
     */
    public static final String PHONE_PATTERN = "^\\+[1-9]\\d{7,14}$";

    /** At least one letter and one digit. Length is checked separately, with its own message. */
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).+$";

    /** Exactly six digits, leading zeros included (FR-A2). */
    public static final String OTP_PATTERN = "^\\d{6}$";

    public static final int PASSWORD_MIN_LENGTH = 8;

    /** BCrypt ignores anything past 72 bytes, so accepting more would silently not count. */
    public static final int PASSWORD_MAX_LENGTH = 72;

    private AuthValidation() {
    }
}
