package com.tutorspoint.common.exception;

/**
 * The caller is authenticated but not permitted to act on this resource
 * (a tutor editing another tutor's profile). Maps to 403.
 */
public class UnauthorizedActionException extends TutorsPointException {

    private static final String CODE = "UNAUTHORIZED_ACTION";

    public UnauthorizedActionException(String message) {
        super(CODE, message);
    }
}
