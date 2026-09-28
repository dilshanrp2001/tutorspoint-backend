package com.tutorspoint.common.storage;

import com.tutorspoint.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;

/**
 * {@link FileStorage} on the filesystem: the MVP implementation, and the only class in the
 * application that knows a filesystem is involved.
 *
 * <p>Adequate for a single deployable behind one process, which is what the MVP is. It is also
 * the reason the interface it implements mentions no path: replacing this with an
 * S3-compatible store is a new class next to this one and a line of configuration, with no
 * caller touched.
 *
 * <p>The security-relevant part is {@link #resolve}. A key reaching this class may have come
 * from a URL, so every one is parsed against {@link StorageKeys} and then re-checked after
 * normalisation to be certain the path it produced is still inside the root. Two independent
 * checks for one rule is deliberate: path traversal is the failure this class exists to
 * prevent, and the pattern alone would be one regex mistake away from a directory listing of
 * the host.
 */
@Slf4j
@Component
public class LocalDiskFileStorage implements FileStorage {

    private final Path root;
    private final Clock clock;

    public LocalDiskFileStorage(LocalDiskStorageProperties properties, Clock clock) {
        this.root = properties.root().toAbsolutePath().normalize();
        this.clock = clock;
        createRoot();
    }

    @Override
    public String store(StorageArea area, FileContent content) {
        String storageKey = StorageKeys.create(area, content.type(), clock);
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
            // CREATE_NEW rather than an overwrite: a generated UUID cannot collide in practice,
            // and if one ever did, failing is enormously better than silently replacing
            // somebody else's file.
            Files.write(target, content.bytes(), StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new FileStorageException("Could not write " + storageKey, e);
        }
        log.debug("Stored {} ({} bytes)", storageKey, content.sizeBytes());
        return storageKey;
    }

    @Override
    public FileContent retrieve(String storageKey) {
        StorageKeys.ParsedKey key = StorageKeys.require(storageKey);
        try {
            return new FileContent(key.type(), Files.readAllBytes(resolve(storageKey)));
        } catch (NoSuchFileException e) {
            throw new ResourceNotFoundException("Stored file", storageKey);
        } catch (IOException e) {
            throw new FileStorageException("Could not read " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            boolean removed = Files.deleteIfExists(resolve(storageKey));
            log.debug("Deleted {} (existed: {})", storageKey, removed);
        } catch (IOException e) {
            throw new FileStorageException("Could not delete " + storageKey, e);
        }
    }

    /**
     * Turns a key into a path, refusing anything that is not a key we could have issued and
     * anything that resolves outside the root.
     */
    private Path resolve(String storageKey) {
        StorageKeys.ParsedKey key = StorageKeys.require(storageKey);
        Path resolved = root.resolve(key.value()).normalize();
        if (!resolved.startsWith(root)) {
            // Unreachable given the key pattern, and kept precisely because that sentence is
            // the kind that stops being true after somebody edits a regex.
            throw new FileStorageException("Storage key escapes the storage root: " + storageKey);
        }
        return resolved;
    }

    private void createRoot() {
        try {
            Files.createDirectories(root);
            log.info("File storage root: {}", root);
        } catch (IOException e) {
            throw new FileStorageException("Could not create the storage root " + root, e);
        }
    }
}
