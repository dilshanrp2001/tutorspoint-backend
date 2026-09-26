package com.tutorspoint.common.exception;

/**
 * The caller sent a file the platform will not take: the wrong format, or too big. Maps to
 * 400.
 *
 * <p>Distinct from a validation failure on a DTO field, because there is no field to point
 * at - the offending value is the body itself. The {@code code} says which rule was broken so
 * the client can say so in the caller's language.
 */
public class InvalidUploadException extends TutorsPointException {

    /** The bytes are not one of the formats this upload accepts. */
    public static final String TYPE_NOT_ALLOWED = "UPLOAD_TYPE_NOT_ALLOWED";

    /** The file is over the size limit for this upload. */
    public static final String TOO_LARGE = "UPLOAD_TOO_LARGE";

    /** Nothing was sent, or the part was empty. */
    public static final String EMPTY = "UPLOAD_EMPTY";

    public InvalidUploadException(String code, String message) {
        super(code, message);
    }
}
