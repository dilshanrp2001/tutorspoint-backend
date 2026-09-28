package com.tutorspoint.common.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Files following a transaction's outcome, driven through the synchronization callbacks the
 * transaction manager itself would invoke.
 */
@ExtendWith(MockitoExtension.class)
class StoredFilesTest {

    private static final String KEY = "documents/2026/09/0f8c2b9e-1111-4a7e-9d9d-2c1e5b6a7f80.pdf";

    @Mock
    private FileStorage storage;

    @AfterEach
    void endSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void aNewFileIsRemovedWhenTheTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        StoredFiles.deleteOnRollback(storage, KEY);

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage).delete(KEY);
    }

    @Test
    void aNewFileIsKeptWhenTheTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();
        StoredFiles.deleteOnRollback(storage, KEY);

        commit();

        verify(storage, never()).delete(KEY);
    }

    @Test
    void aReplacedFileIsRemovedOnlyOnceTheTransactionHasCommitted() {
        TransactionSynchronizationManager.initSynchronization();
        StoredFiles.deleteAfterCommit(storage, KEY);
        verify(storage, never()).delete(KEY);

        commit();

        verify(storage).delete(KEY);
    }

    @Test
    void aReplacedFileSurvivesARollback() {
        TransactionSynchronizationManager.initSynchronization();
        StoredFiles.deleteAfterCommit(storage, KEY);

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage, never()).delete(KEY);
    }

    @Test
    void aStoreThatRefusesTheCleanupDoesNotThrowOutOfACompletedTransaction() {
        doThrow(new FileStorageException("disk gone")).when(storage).delete(KEY);
        TransactionSynchronizationManager.initSynchronization();
        StoredFiles.deleteOnRollback(storage, KEY);

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage).delete(KEY);
    }

    @Test
    void outsideATransactionTheWorkHasAlreadyCommitted() {
        StoredFiles.deleteOnRollback(storage, KEY);
        verify(storage, never()).delete(KEY);

        StoredFiles.deleteAfterCommit(storage, KEY);
        verify(storage).delete(KEY);
    }

    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        complete(TransactionSynchronization.STATUS_COMMITTED);
    }

    private static void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(status));
    }
}
