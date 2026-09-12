package com.tutorspoint.common.exception;

/**
 * The caller could not be identified: wrong credentials, or a missing, malformed, expired
 * or revoked token. Maps to 401.
 *
 * <p>Distinct from {@link UnauthorizedActionException}, which is 403 — that caller is
 * known, they simply may not do this. The difference matters to the client: a 401 means
 * "refresh or sign in again", a 403 means "stop asking".
 *
 * <p>The {@code code} is deliberately coarse on the sign-in path. Telling a caller
 * whether the email or the password was wrong hands them an account enumeration oracle.
 */
public class AuthenticationFailedException extends TutorsPointException {

    private static final String CODE = "AUTHENTICATION_FAILED";

    public AuthenticationFailedException(String message) {
        super(CODE, message);
    }

    public AuthenticationFailedException(String code, String message) {
        super(code, message);
    }
}
