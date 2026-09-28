package com.tutorspoint.common.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Ties files in {@link FileStorage} to the database transaction that references them (NFR-7).
 *
 * <p>A file store is not transactional, so a service that writes a file and a row can only be
 * atomic if the file follows the transaction's outcome. Two rules make it so:
 *
 * <ul>
 *   <li><strong>A new file is removed if the transaction rolls back.</strong> Otherwise a failed
 *       upload leaves an orphan behind - and for a verification document that orphan is a copy
 *       of somebody's identity card that nothing refers to and nobody will ever delete.</li>
 *   <li><strong>A replaced file is removed only after the transaction commits.</strong> Otherwise
 *       a commit that fails leaves the row pointing at a file that is already gone.</li>
 * </ul>
 *
 * <p>Called outside a transaction, the caller's writes have already committed, so an after-commit
 * delete runs at once and a rollback cleanup has nothing to guard.
 */
@Slf4j
public final class StoredFiles {

    private StoredFiles() {
    }

    /** Deletes {@code storageKey} if, and only if, the current transaction rolls back. */
    public static void deleteOnRollback(FileStorage storage, String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(storage, storageKey);
                }
            }
        });
    }

    /** Deletes {@code storageKey} once the current transaction has committed. */
    public static void deleteAfterCommit(FileStorage storage, String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storage.delete(storageKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(storage, storageKey);
            }
        });
    }

    /**
     * After completion there is nobody left to tell: the response is decided and the transaction
     * is over. A file that could not be removed is an unreferenced file, which is logged by key -
     * a key names no person - so it can be swept.
     */
    private static void deleteQuietly(FileStorage storage, String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (RuntimeException e) {
            log.error("Could not remove stored file {} after its transaction ended; it is now unreferenced",
                    storageKey, e);
        }
    }
}
