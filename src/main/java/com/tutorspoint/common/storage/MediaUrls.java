package com.tutorspoint.common.storage;

import java.util.Optional;

/**
 * The one translation between a storage key and the address a browser fetches it from.
 *
 * <p>Public media is addressed by its key under {@link #BASE_PATH}, so nothing has to be
 * stored twice and no signing or expiry is involved - these are photographs meant to be seen.
 * The key is still unguessable, which is what keeps an unpublished profile's photo from being
 * enumerable while the profile itself is hidden.
 *
 * <p>Application-relative on purpose. A stored absolute URL would bake today's host into every
 * row, and moving to a CDN would become a data migration instead of a change here.
 */
public final class MediaUrls {

    public static final String BASE_PATH = "/api/media/";

    private MediaUrls() {
    }

    /** The public address of a stored file. */
    public static String urlFor(String storageKey) {
        return BASE_PATH + storageKey;
    }

    /**
     * The key behind one of our own media URLs, or empty if the value is not one - a profile
     * whose photo predates this scheme, or was never a media URL at all.
     */
    public static Optional<String> keyFrom(String url) {
        if (url == null || !url.startsWith(BASE_PATH)) {
            return Optional.empty();
        }
        return StorageKeys.parse(url.substring(BASE_PATH.length())).map(StorageKeys.ParsedKey::value);
    }
}
