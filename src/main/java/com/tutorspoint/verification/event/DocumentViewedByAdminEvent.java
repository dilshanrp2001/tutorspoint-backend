package com.tutorspoint.verification.event;

import com.tutorspoint.common.audit.AuditAction;
import com.tutorspoint.common.audit.AuditEntry;
import com.tutorspoint.common.audit.AuditTargetType;
import com.tutorspoint.common.audit.AuditableEvent;

/**
 * An administrator has opened somebody's submitted document.
 *
 * <p>Changes nothing, and is audited anyway: a member of staff reading a tutor's identity card
 * is exactly the kind of access NFR-10 exists to account for. The owner reading their own file
 * is not an event at all.
 *
 * @param adminId    who read it
 * @param documentId what they read
 * @param tutorId    whose document it is, so the record answers "who has seen my NIC"
 */
public record DocumentViewedByAdminEvent(Long adminId, Long documentId, Long tutorId) implements AuditableEvent {

    @Override
    public AuditEntry auditEntry() {
        return new AuditEntry(adminId, AuditAction.DOCUMENT_VIEWED, AuditTargetType.VERIFICATION_DOCUMENT, documentId,
                null, AuditEntry.values("tutorId", tutorId), null);
    }
}
