package com.tutorspoint.common.storage;

import java.util.Arrays;
import java.util.Optional;

/**
 * A file type the platform accepts, recognised by what the bytes actually are.
 *
 * <p><strong>The extension and the declared content type are both attacker input.</strong> A
 * browser sends whatever the multipart part says, and a filename ending in {@code .pdf} says
 * nothing at all about what follows. So each value here carries the signature its format
 * begins with, {@link #detect} reads the leading bytes, and everything downstream - the
 * allow-list, the stored extension, the {@code Content-Type} on the way back out - is derived
 * from that answer rather than from anything the client claimed.
 *
 * <p>The extension is ours, not the uploader's: it is appended to a generated key, so the
 * stored name can never carry a path, a second extension, or a surprise.
 */
public enum FileType {

    /** {@code %PDF-} */
    PDF("application/pdf", "pdf", 0, 0x25, 0x50, 0x44, 0x46, 0x2D),

    /** SOI marker, then the first segment. Covers JFIF, Exif and raw JPEG alike. */
    JPEG("image/jpeg", "jpg", 0, 0xFF, 0xD8, 0xFF),

    PNG("image/png", "png", 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),

    /**
     * ISO base media: a size field, then {@code ftyp} at offset four. The brand that follows
     * varies (isom, mp42, avc1) and is not checked - the container is what matters here.
     */
    MP4("video/mp4", "mp4", 4, 0x66, 0x74, 0x79, 0x70);

    private final String contentType;
    private final String extension;
    private final int signatureOffset;
    private final byte[] signature;

    FileType(String contentType, String extension, int signatureOffset, int... signature) {
        this.contentType = contentType;
        this.extension = extension;
        this.signatureOffset = signatureOffset;
        this.signature = new byte[signature.length];
        for (int i = 0; i < signature.length; i++) {
            this.signature[i] = (byte) signature[i];
        }
    }

    /**
     * What these bytes are, or empty if they are nothing we accept.
     *
     * <p>Empty is the honest answer for an unrecognised file, and the caller turns it into a
     * rejection. Guessing from the filename instead would defeat the whole point of looking.
     */
    public static Optional<FileType> detect(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.matches(content))
                .findFirst();
    }

    /** The type stored against a key we generated, recovered from the extension we appended. */
    static Optional<FileType> fromExtension(String extension) {
        if (extension == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.extension.equalsIgnoreCase(extension))
                .findFirst();
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public boolean isImage() {
        return this == JPEG || this == PNG;
    }

    private boolean matches(byte[] content) {
        if (content.length < signatureOffset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[signatureOffset + i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
