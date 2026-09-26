package com.tutorspoint.verification;

import com.tutorspoint.auth.domain.Role;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.AuthenticatedUser;
import com.tutorspoint.auth.security.CurrentUser;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.common.exception.ResourceNotFoundException;
import com.tutorspoint.common.exception.UnauthorizedActionException;
import com.tutorspoint.common.storage.FileContent;
import com.tutorspoint.common.storage.FileStorage;
import com.tutorspoint.common.storage.FileType;
import com.tutorspoint.common.storage.StorageArea;
import com.tutorspoint.common.storage.StoredFiles;
import com.tutorspoint.common.storage.UploadedFile;
import com.tutorspoint.verification.domain.DocumentType;
import com.tutorspoint.verification.domain.VerificationDocument;
import com.tutorspoint.verification.dto.VerificationDocumentResponse;
import com.tutorspoint.verification.event.DocumentViewedByAdminEvent;
import com.tutorspoint.verification.repository.VerificationDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Document submission and retrieval.
 *
 * <p>Three things are worth reading closely.
 *
 * <p><strong>The file is checked before it is stored, from its bytes.</strong>
 * {@link UploadedFile#detectType()} reads the signature and {@link StorageArea#ensureAccepts}
 * applies the area's allow-list and cap, so a {@code .pdf} full of something else, and a file
 * declared as {@code application/pdf} that is not one, are both refused before anything is
 * written.
 *
 * <p><strong>Writing the file and writing the row can disagree, and the order chosen decides
 * how.</strong> The file is stored first: if the transaction then fails, an orphan object is
 * left in the store - invisible, unreferenced and cheap to sweep. The other order risks a row
 * pointing at a file that does not exist, which is a broken document in a reviewer's queue.
 * The unpleasant case is chosen deliberately over the visible one.
 *
 * <p><strong>Reading is authorised, never scoped, for exactly one method.</strong> Everything
 * a tutor does goes through a query that filters by their own id; {@link #download} cannot,
 * because an administrator is entitled to any document, so it is the one place an explicit
 * check is written - and it answers "not found" rather than "forbidden" so that the endpoint
 * reveals nothing to somebody guessing ids.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationDocumentServiceImpl implements VerificationDocumentService {

    /** Enough for an NIC, a degree, a teaching certificate and several more. */
    static final long MAX_DOCUMENTS_PER_TUTOR = 10;

    private static final String ERROR_TOO_MANY_DOCUMENTS = "TOO_MANY_DOCUMENTS";

    private final UserRepository users;
    private final VerificationDocumentRepository documents;
    private final FileStorage fileStorage;
    private final VerificationMapper verificationMapper;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;

    @Override
    @PreAuthorize("hasRole('TUTOR')")
    @Transactional
    public VerificationDocumentResponse upload(DocumentType documentType, UploadedFile file) {
        // The caller is read once and used throughout: the id in the token is the owner of the
        // upload, the subject of the limit, and what the log line is about.
        Long callerId = currentUser.requireId();
        Tutor tutor = requireTutor(callerId);
        ensureRoomForAnother(callerId);

        FileType type = file.detectType();
        StorageArea.TUTOR_DOCUMENTS.ensureAccepts(type, file.sizeBytes());

        FileContent content = file.asContent();
        String storageKey = fileStorage.store(StorageArea.TUTOR_DOCUMENTS, content);
        // The file and the row are one upload (NFR-7). If the row cannot be written, or the
        // transaction fails to commit, the file goes too.
        StoredFiles.deleteOnRollback(fileStorage, storageKey);
        VerificationDocument document =
                documents.save(new VerificationDocument(tutor, documentType, storageKey, file, content));

        log.info("Tutor {} uploaded {} document {} ({} bytes)",
                callerId, documentType, document.getId(), content.sizeBytes());
        return verificationMapper.toResponse(document);
    }

    @Override
    @PreAuthorize("hasRole('TUTOR')")
    @Transactional(readOnly = true)
    public List<VerificationDocumentResponse> myDocuments() {
        return verificationMapper.toResponses(
                documents.findByTutorIdOrderByCreatedAtDesc(currentUser.requireId()));
    }

    @Override
    @PreAuthorize("hasRole('TUTOR')")
    @Transactional
    public void deleteMyDocument(Long documentId) {
        Long callerId = currentUser.requireId();
        VerificationDocument document = documents.findByIdAndTutorId(documentId, callerId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
        document.ensureWithdrawable();

        documents.delete(document);
        // The file goes after the row and inside the same transaction, so a store that refuses
        // the delete rolls the row back too and the tutor still has their document. The
        // remaining window - a commit that fails after the file is gone - leaves a row pointing
        // at nothing, which is the rarer failure and the one a re-upload fixes.
        fileStorage.delete(document.getStorageKey());
        log.info("Tutor {} withdrew document {}", callerId, documentId);
    }

    /**
     * Authorises, then reads. The order is the point: nothing is fetched from the store until
     * the caller has been established as the owner or an administrator.
     *
     * <p>Not read-only, although it changes no document: an administrator's read writes an audit
     * row in this transaction, and a read-only one would refuse the insert.
     */
    @Override
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public DocumentDownload download(Long documentId) {
        AuthenticatedUser caller = currentUser.require();
        VerificationDocument document = documents.findById(documentId)
                .filter(found -> found.belongsTo(caller.userId()) || caller.role() == Role.ADMIN)
                // Deliberately the same answer as an id that does not exist. A 403 here would
                // confirm to a stranger that document 41 is somebody's NIC.
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        if (caller.role() == Role.ADMIN) {
            // Audited (architecture section 11, NFR-10): a member of staff reading somebody's
            // identity document is exactly the kind of access that has to leave a trace.
            events.publishEvent(new DocumentViewedByAdminEvent(
                    caller.userId(), documentId, document.getTutor().getId()));
        }
        return new DocumentDownload(fileStorage.retrieve(document.getStorageKey()), document.getOriginalFilename());
    }

    private void ensureRoomForAnother(Long tutorId) {
        if (documents.countByTutorId(tutorId) >= MAX_DOCUMENTS_PER_TUTOR) {
            throw new BusinessRuleViolationException(ERROR_TOO_MANY_DOCUMENTS,
                    "A tutor may hold at most %d documents; withdraw one before adding another"
                            .formatted(MAX_DOCUMENTS_PER_TUTOR));
        }
    }

    private Tutor requireTutor(Long callerId) {
        return users.findById(callerId)
                .filter(Tutor.class::isInstance)
                .map(Tutor.class::cast)
                // Unreachable through the HTTP routes, which the role rule already guards.
                // Kept so a future non-HTTP caller fails loudly instead of casting blindly.
                .orElseThrow(() -> new UnauthorizedActionException(
                        "Account %s is not a tutor account".formatted(callerId)));
    }
}
