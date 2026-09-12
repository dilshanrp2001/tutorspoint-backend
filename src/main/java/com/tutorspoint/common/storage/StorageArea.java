package com.tutorspoint.common.storage;

import com.tutorspoint.common.exception.InvalidUploadException;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * A kind of file the platform stores, and the whole policy that goes with it: where its keys
 * live, what formats it accepts, how big it may be, and whether anybody may read it.
 *
 * <p>The policy is here rather than spread across the services that upload, because these are
 * the questions that must never be answered differently in two places. A photo endpoint that
 * forgot the size cap, or a document endpoint that accepted a format the reviewer cannot open,
 * would each be one missing {@code if}; there is no {@code if} to miss when the rule travels
 * with the area.
 *
 * <p>{@link Visibility} is a statement about the content, not an enforcement mechanism. What
 * enforces it is the media endpoint refusing to serve a private area at all, and the document
 * endpoint authorising every single read.
 */
public enum StorageArea {

    /**
     * Qualification documents awaiting review (FR-T7). Private: a NIC scan is the most
     * sensitive thing on the platform. PDF or a photograph of the certificate, because that is
     * what a tutor on a phone actually has.
     */
    TUTOR_DOCUMENTS("documents", Visibility.PRIVATE, 5, Set.of(FileType.PDF, FileType.JPEG, FileType.PNG)),

    /** Profile photographs. Public - they are the profile (FR-T1). */
    PROFILE_PHOTOS("photos", Visibility.PUBLIC, 5, Set.of(FileType.JPEG, FileType.PNG)),

    /**
     * Introduction videos (FR-T1). Public, and capped low on purpose: the platform serves
     * these itself, and the audience is on a phone paying for data.
     */
    INTRO_VIDEOS("videos", Visibility.PUBLIC, 5, Set.of(FileType.MP4));

    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

    private final String prefix;
    private final Visibility visibility;
    private final long maxBytes;
    private final Set<FileType> accepted;

    /**
     * Limits are declared in megabytes, which is how they are discussed and how they read in an
     * API description. The conversion is here because a static constant cannot be referenced
     * from an enum constant's arguments - they are initialised first.
     */
    StorageArea(String prefix, Visibility visibility, int maxMegabytes, Set<FileType> accepted) {
        this.prefix = prefix;
        this.visibility = visibility;
        this.maxBytes = maxMegabytes * BYTES_PER_MEGABYTE;
        this.accepted = Set.copyOf(accepted);
    }

    /**
     * The area a key belongs to, read from the prefix it was built with, or empty for a prefix
     * we never issued. Empty rather than an exception because the caller is parsing untrusted
     * input, where an unknown prefix is an ordinary answer.
     */
    static Optional<StorageArea> ofPrefix(String prefix) {
        return Arrays.stream(values())
                .filter(area -> area.prefix.equals(prefix))
                .findFirst();
    }

    /**
     * Accepts or rejects a file for this area.
     *
     * @throws InvalidUploadException if the file is empty, over the cap, or not a format this
     *                                area takes
     */
    public void ensureAccepts(FileType type, long sizeBytes) {
        if (sizeBytes <= 0) {
            throw new InvalidUploadException(InvalidUploadException.EMPTY, "The uploaded file is empty");
        }
        if (sizeBytes > maxBytes) {
            throw new InvalidUploadException(InvalidUploadException.TOO_LARGE,
                    "This upload is limited to %d bytes; the file is %d".formatted(maxBytes, sizeBytes));
        }
        if (type == null || !accepted.contains(type)) {
            throw new InvalidUploadException(InvalidUploadException.TYPE_NOT_ALLOWED,
                    "This upload accepts %s".formatted(acceptedContentTypes()));
        }
    }

    public boolean isPublic() {
        return visibility == Visibility.PUBLIC;
    }

    public long maxBytes() {
        return maxBytes;
    }

    /** For the API description and the client-side file picker, in a stable order. */
    public String acceptedContentTypes() {
        return accepted.stream().map(FileType::contentType).sorted().reduce((a, b) -> a + ", " + b).orElse("");
    }

    String prefix() {
        return prefix;
    }

    /** Who may read what is in an area, once it is stored. */
    private enum Visibility {

        /** Anyone with the link, including a guest. */
        PUBLIC,

        /** The owner and an administrator, through an endpoint that checks before it streams. */
        PRIVATE
    }
}
