package com.tutorspoint.admin.event;

import com.tutorspoint.common.audit.AuditAction;
import com.tutorspoint.common.audit.AuditEntry;
import com.tutorspoint.common.audit.AuditTargetType;
import com.tutorspoint.common.audit.AuditableEvent;
import com.tutorspoint.verification.domain.DocumentStatus;

/**
 * An administrator has ruled on a submitted document.
 *
 * <p>Audited today; the natural place for Phase 6's "your document was rejected" notification to
 * listen tomorrow, without the review service changing.
 *
 * @param adminId    who ruled
 * @param documentId what was ruled on
 * @param tutorId    whose document it is
 * @param decision   APPROVED or REJECTED
 * @param notes      the reviewer's notes, which for a rejection are the reason shown to the tutor
 */
public record DocumentReviewedEvent(
        Long adminId,
        Long documentId,
        Long tutorId,
        DocumentStatus decision,
        String notes) implements AuditableEvent {

    @Override
    public AuditEntry auditEntry() {
        AuditAction action = decision == DocumentStatus.APPROVED
                ? AuditAction.DOCUMENT_APPROVED
                : AuditAction.DOCUMENT_REJECTED;
        return new AuditEntry(adminId, action, AuditTargetType.VERIFICATION_DOCUMENT, documentId,
                AuditEntry.values("status", DocumentStatus.PENDING),
                AuditEntry.values("status", decision, "reviewNotes", notes, "tutorId", tutorId),
                null);
    }
}
