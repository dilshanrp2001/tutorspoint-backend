package com.tutorspoint.verification.domain;

import com.tutorspoint.auth.domain.Admin;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.common.domain.BaseEntity;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.common.storage.FileContent;
import com.tutorspoint.common.storage.UploadedFile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * A document a tutor submitted to prove a claim, and the review it is waiting for or has had
 * (FR-T7).
 *
 * <p>It holds a {@code storageKey}, never bytes. The row is what the platform knows about the
 * file - who sent it, what they say it is, what it actually is, and what a reviewer decided -
 * while the file itself lives behind {@code FileStorage}. That separation is why moving to
 * object storage later touches no data: the key is opaque to this table too.
 *
 * <p>The three recorded facts about the content ({@code contentType}, {@code sizeBytes},
 * {@code originalFilename}) are a record of what was accepted, not a description to be trusted
 * later. The content type is the one the platform detected from the bytes, not the one the
 * client declared, and the filename is a label for the tutor's own list.
 *
 * <p>Two invariants live here rather than in a service. A decision is final and always has an
 * author and a moment; and a document may be withdrawn only before somebody has ruled on it.
 */
@Entity
@Table(name = "verification_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationDocument extends BaseEntity {

    /** Stable keys the client switches on and translates into si / ta / en. */
    private static final String ERROR_ALREADY_REVIEWED = "DOCUMENT_ALREADY_REVIEWED";
    private static final String ERROR_NOT_PENDING = "DOCUMENT_NOT_PENDING";

    /** The reason a rejection has to be readable: the tutor has to know what to send instead. */
    public static final int MAX_REVIEW_NOTES = 1000;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false, updatable = false)
    private Tutor tutor;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    /** Opaque, generated, and the only handle on the file. Never shown to a client. */
    @Column(name = "storage_key", nullable = false, length = 200, updatable = false)
    private String storageKey;

    /** What the tutor called it. A label, never used to locate, name or identify the file. */
    @Column(name = "original_filename", nullable = false, length = 255, updatable = false)
    private String originalFilename;

    /** Detected from the bytes at upload. What the download endpoint sends back. */
    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "review_notes", length = MAX_REVIEW_NOTES)
    private String reviewNotes;

    /** Who decided. Kept for audit (architecture section 11), which is why it is an account. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private Admin reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /**
     * Records a document that has just been stored.
     *
     * <p>Takes the stored {@link FileContent} rather than loose strings, so the type and size
     * written to the row are necessarily the ones that were stored - not a caller's second
     * opinion about the same file.
     */
    public VerificationDocument(Tutor tutor,
                                DocumentType documentType,
                                String storageKey,
                                UploadedFile upload,
                                FileContent stored) {
        this.tutor = requireNotNull(tutor, "tutor");
        this.documentType = requireNotNull(documentType, "documentType");
        this.storageKey = requireText(storageKey, "storageKey");
        this.originalFilename = requireNotNull(upload, "upload").originalFilename();
        this.contentType = requireNotNull(stored, "stored").contentType();
        this.sizeBytes = stored.sizeBytes();
        this.status = DocumentStatus.PENDING;
    }

    /**
     * A reviewer accepts the document (Phase 6 drives this).
     *
     * @throws BusinessRuleViolationException if it has already been ruled on
     */
    public void approve(Admin reviewer, String notes, Instant at) {
        recordDecision(DocumentStatus.APPROVED, reviewer, notes, at);
    }

    /**
     * A reviewer refuses it. The notes are what the tutor is shown, so a rejection is required
     * to say why - a refusal with no reason produces a re-upload of the same file.
     *
     * @throws BusinessRuleViolationException if it has already been ruled on
     * @throws IllegalArgumentException       if no reason is given
     */
    public void reject(Admin reviewer, String reason, Instant at) {
        recordDecision(DocumentStatus.REJECTED, reviewer, requireText(reason, "reason"), at);
    }

    /**
     * Whether the tutor may still withdraw this.
     *
     * @throws BusinessRuleViolationException if a reviewer has already ruled on it
     */
    public void ensureWithdrawable() {
        if (status != DocumentStatus.PENDING) {
            throw new BusinessRuleViolationException(ERROR_NOT_PENDING,
                    "Document %s is %s and can no longer be withdrawn".formatted(getId(), status));
        }
    }

    public boolean isPending() {
        return status == DocumentStatus.PENDING;
    }

    public boolean isApproved() {
        return status == DocumentStatus.APPROVED;
    }

    /** Ownership check for the rule that a tutor sees only their own documents. */
    public boolean belongsTo(Long userId) {
        return userId != null && Objects.equals(tutor.getId(), userId);
    }

    private void recordDecision(DocumentStatus decision, Admin reviewer, String notes, Instant at) {
        if (status != DocumentStatus.PENDING) {
            throw new BusinessRuleViolationException(ERROR_ALREADY_REVIEWED,
                    "Document %s was already %s".formatted(getId(), status));
        }
        this.status = decision;
        this.reviewedBy = requireNotNull(reviewer, "reviewer");
        this.reviewedAt = requireNotNull(at, "at");
        this.reviewNotes = trimToLimit(notes);
    }

    private static String trimToLimit(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        String trimmed = notes.trim();
        return trimmed.length() > MAX_REVIEW_NOTES ? trimmed.substring(0, MAX_REVIEW_NOTES) : trimmed;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }
}
