package com.tutorspoint.common.storage;

import com.tutorspoint.common.exception.ResourceNotFoundException;

/**
 * Where uploaded files live. The application's only way to keep bytes it did not generate.
 *
 * <p><strong>Nothing about a disk appears in this contract.</strong> No {@code Path}, no
 * {@code File}, no directory, no URL - a key goes in, a key comes back, and the caller never
 * learns whether the bytes went to a mounted volume, an S3 bucket or a test's hash map. That
 * is the whole design constraint: the MVP writes to local disk, object storage arrives later,
 * and the day it does the change must be a new class and one line of configuration rather
 * than an edit to every service that ever stored a file.
 *
 * <p>Two consequences of that are worth stating, because they look like limitations until you
 * try the alternative:
 *
 * <ul>
 *   <li><strong>The key is opaque and ours.</strong> Callers store it and hand it back; they
 *       must not parse it, build one, or show it to a user. The uploader's filename never
 *       becomes part of it, so no upload can name a path, hide a second extension, or collide
 *       with somebody else's file.</li>
 *   <li><strong>Content is passed whole, in memory.</strong> Every area caps uploads at a few
 *       megabytes ({@link StorageArea}), so this is bounded and it keeps both implementations
 *       and their tests simple. A future area that must stream something large gets a
 *       streaming method of its own rather than a change to this one.</li>
 * </ul>
 */
public interface FileStorage {

    /**
     * Stores content and returns the key it can be read back with.
     *
     * <p>The key is generated, never derived from the uploader's filename. Storing the same
     * bytes twice yields two keys and two objects: de-duplication is not this layer's business,
     * and two tutors uploading the same certificate must not end up sharing one file that
     * either of them can delete.
     *
     * @throws FileStorageException if the content could not be written
     */
    String store(StorageArea area, FileContent content);

    /**
     * Reads content back.
     *
     * @throws ResourceNotFoundException if the key names nothing - a deleted document and a
     *                                   made-up key answer identically
     * @throws FileStorageException      if the content exists but could not be read
     */
    FileContent retrieve(String storageKey);

    /**
     * Removes content. Idempotent: deleting what is already gone is success, because the
     * caller's intent - that this file no longer exist - is satisfied either way.
     *
     * @throws FileStorageException if the content exists but could not be removed
     */
    void delete(String storageKey);
}
