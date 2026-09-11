package com.tutorspoint.common.exception;

import lombok.Getter;

/**
 * Base type for every exception this application throws deliberately.
 * The {@code code} is the stable key returned to clients and used for translation.
 */
@Getter
public abstract class TutorsPointException extends RuntimeException {

    private final String code;

    protected TutorsPointException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected TutorsPointException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
