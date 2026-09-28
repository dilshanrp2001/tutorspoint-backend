package com.tutorspoint.common.ratelimit;

/**
 * The actions worth throttling at the edge: the ones a script would hammer. Signing in and
 * recovery are guessing targets, registration and OTP requests cost an email or an SMS each,
 * enquiry creation lands in a tutor's inbox, and search is the most expensive read there is.
 *
 * <p>Each is configured under {@code tutorspoint.rate-limit.actions} by its kebab-case name.
 */
public enum RateLimitedAction {
    LOGIN,
    REGISTER,
    OTP_REQUEST,
    PASSWORD_RESET,
    ENQUIRY_CREATION,
    SEARCH
}
