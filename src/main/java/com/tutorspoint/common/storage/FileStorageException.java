package com.tutorspoint.common.storage;

import com.tutorspoint.common.exception.TutorsPointException;

/**
 * The store could not be read or written: a full disk, a permission the process does not
 * have, an object store that timed out.
 *
 * <p>Always a fault on our side, never the caller's - a file the caller sent that we refuse
 * is {@link com.tutorspoint.common.exception.InvalidUploadException}, and a key that names
 * nothing is a {@code ResourceNotFoundException}. Keeping the three apart is what lets the
 * error advice answer 500, 400 and 404 without inspecting a message.
 */
public class FileStorageException extends TutorsPointException {

    private static final String CODE = "STORAGE_FAILURE";

    public FileStorageException(String message, Throwable cause) {
        super(CODE, message, cause);
    }

    public FileStorageException(String message) {
        super(CODE, message);
    }
}
