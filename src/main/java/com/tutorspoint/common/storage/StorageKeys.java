package com.tutorspoint.common.storage;

import com.tutorspoint.common.exception.ResourceNotFoundException;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The shape of a storage key, and the only place it is built or read.
 *
 * <p>{@code documents/2026/09/9f2c…a1.pdf} - an area prefix, the year and month it arrived,
 * a random name, and the extension for what the bytes turned out to be.
 *
 * <p>Each part earns its place. The prefix is how an implementation groups objects and how
 * the media endpoint knows a key is private without a database round trip. The date keeps any
 * one directory from growing to a million entries on a filesystem that does not enjoy that.
 * The {@link UUID} is the security-relevant part: keys are unguessable, they carry nothing of
 * the uploader's filename, and two files can never collide.
 *
 * <p>{@link #parse} is the counterpart, and it is deliberately strict. A key that arrives from
 * outside - in a URL, or read back from a row - is untrusted input, and the pattern below is
 * what stops {@code ../../} from ever reaching a filesystem call.
 */
public final class StorageKeys {

    /** {@code <prefix>/<yyyy>/<mm>/<uuid>.<ext>} and nothing else: no dots, no traversal. */
    private static final Pattern KEY = Pattern.compile(
            "^(?<prefix>[a-z]+)/(?<year>\\d{4})/(?<month>\\d{2})/"
                    + "(?<name>[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"
                    + "\\.(?<extension>[a-z0-9]{2,5})$");

    private StorageKeys() {
    }

    /** A fresh key for content about to be stored. */
    public static String create(StorageArea area, FileType type, Clock clock) {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
        return "%s/%04d/%02d/%s.%s".formatted(
                area.prefix(), now.getYear(), now.getMonthValue(), UUID.randomUUID(), type.extension());
    }

    /**
     * Reads a key back, or empty if it is not one we could have issued.
     *
     * <p>Empty rather than an exception: every caller of this is handling input from outside,
     * and "this is not a key" is an ordinary answer there, not an exceptional one.
     */
    public static Optional<ParsedKey> parse(String storageKey) {
        if (storageKey == null) {
            return Optional.empty();
        }
        var matcher = KEY.matcher(storageKey);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return StorageArea.ofPrefix(matcher.group("prefix")).flatMap(area ->
                FileType.fromExtension(matcher.group("extension"))
                        .map(type -> new ParsedKey(area, type, storageKey)));
    }

    /**
     * As {@link #parse}, for a key that came from our own database rather than from a client.
     *
     * @throws ResourceNotFoundException if it is unreadable, which by then means the row is
     *                                   corrupt rather than that the caller did anything wrong
     */
    public static ParsedKey require(String storageKey) {
        return parse(storageKey)
                .orElseThrow(() -> new ResourceNotFoundException("Stored file", storageKey));
    }

    /** A key that has been checked, with the two facts the key itself carries. */
    public record ParsedKey(StorageArea area, FileType type, String value) {
    }
}
