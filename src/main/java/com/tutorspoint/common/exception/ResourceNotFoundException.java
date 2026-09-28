package com.tutorspoint.common.exception;

/** The requested resource does not exist. Maps to 404. */
public class ResourceNotFoundException extends TutorsPointException {

    private static final String CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String message) {
        super(CODE, message);
    }

    public ResourceNotFoundException(String resource, Object id) {
        super(CODE, "%s not found: %s".formatted(resource, id));
    }
}
