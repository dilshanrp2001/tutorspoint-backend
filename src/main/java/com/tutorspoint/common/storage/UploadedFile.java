package com.tutorspoint.common.storage;

import com.tutorspoint.common.exception.InvalidUploadException;

/**
 * A file as it arrived from a caller, before anything about it is believed.
 *
 * <p>This is the boundary type: a controller builds one from the multipart part, and nothing
 * below the controller ever sees a servlet type. The two fields that came from the client are
 * kept only for the record - {@code originalFilename} is shown back to the tutor so they can
 * tell one certificate from another, and it is never used to build a key, choose an extension
 * or decide what the file is. {@link #detectType()} answers that from the bytes.
 */
public record UploadedFile(String originalFilename, byte[] bytes) {

    /** Long enough for a real filename, short enough that the column has a bound. */
    public static final int MAX_FILENAME_LENGTH = 255;

    public UploadedFile {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidUploadException(InvalidUploadException.EMPTY, "The uploaded file is empty");
        }
        originalFilename = sanitise(originalFilename);
    }

    public long sizeBytes() {
        return bytes.length;
    }

    /**
     * What the bytes actually are.
     *
     * @throws InvalidUploadException if they are not a format the platform recognises at all
     */
    public FileType detectType() {
        return FileType.detect(bytes)
                .orElseThrow(() -> new InvalidUploadException(InvalidUploadException.TYPE_NOT_ALLOWED,
                        "The file is not a format this platform accepts"));
    }

    public FileContent asContent() {
        return new FileContent(detectType(), bytes);
    }

    /**
     * Keeps the last path segment and nothing else. A client is free to send
     * {@code ../../etc/passwd} or a name with a newline in it; what is kept is a display label,
     * so it is stripped to something that can be shown and stored without either being true.
     */
    private static String sanitise(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload";
        }
        String lastSegment = filename.replace('\\', '/');
        lastSegment = lastSegment.substring(lastSegment.lastIndexOf('/') + 1);
        lastSegment = lastSegment.replaceAll("\\p{Cntrl}", "").trim();
        if (lastSegment.isBlank()) {
            return "upload";
        }
        return lastSegment.length() > MAX_FILENAME_LENGTH
                ? lastSegment.substring(0, MAX_FILENAME_LENGTH)
                : lastSegment;
    }

    /** Never the bytes. See {@link FileContent#toString()}. */
    @Override
    public String toString() {
        return "UploadedFile(%s, %d bytes)".formatted(originalFilename, bytes.length);
    }
}
