package com.tutorspoint.common.storage;

/**
 * Bytes plus what they are. The unit {@link FileStorage} stores and returns.
 *
 * <p>The type is a {@link FileType} rather than a content-type string, because by the time
 * content reaches this record the bytes have been read and identified - carrying the string
 * would let an unverified one back in through the side door.
 */
public record FileContent(FileType type, byte[] bytes) {

    public FileContent {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes must not be empty");
        }
    }

    public long sizeBytes() {
        return bytes.length;
    }

    public String contentType() {
        return type.contentType();
    }

    /**
     * Deliberately says nothing about the bytes. A record's generated {@code toString} would
     * print an array reference, and a log line is not the place for the contents of somebody's
     * NIC scan either way.
     */
    @Override
    public String toString() {
        return "FileContent(%s, %d bytes)".formatted(type, bytes.length);
    }
}
