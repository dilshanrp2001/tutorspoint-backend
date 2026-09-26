package com.tutorspoint.enquiry.event;

import com.tutorspoint.common.audit.AuditAction;
import com.tutorspoint.common.audit.AuditEntry;
import com.tutorspoint.common.audit.AuditTargetType;
import com.tutorspoint.common.audit.AuditableEvent;
import com.tutorspoint.common.domain.Language;

/**
 * The tutor has replied for the first time — the moment the thread becomes a conversation
 * and contact details become visible on it (FR-E2).
 *
 * <p>Published once per enquiry, on the first reply only. Every later message is an ordinary
 * message: it notifies nobody through this path, because a platform that emails on every
 * message is a platform people turn off.
 *
 * <p>Self-contained for the same reason as {@link EnquiryCreatedEvent}; see the note there.
 *
 * <p>It is also the contact reveal NFR-10 requires a record of, so it is an
 * {@link AuditableEvent}: the reveal is audited because it happened, not because the service
 * remembered to say so. The actor is the tutor, whose reply is what opened the channel.
 *
 * @param enquiryId      the thread, for the deep link the seeker follows
 * @param seekerId       who is being answered — a parent or a student
 * @param seekerEmail    where the notification goes
 * @param seekerName     for the greeting
 * @param seekerLanguage the language the seeker reads
 * @param tutorId        who answered, for the analytics counters
 * @param tutorName      the name of the tutor who replied
 */
public record EnquiryRespondedEvent(
        Long enquiryId,
        Long seekerId,
        String seekerEmail,
        String seekerName,
        Language seekerLanguage,
        Long tutorId,
        String tutorName) implements AuditableEvent {

    @Override
    public AuditEntry auditEntry() {
        return new AuditEntry(tutorId, AuditAction.CONTACT_REVEALED, AuditTargetType.ENQUIRY, enquiryId,
                AuditEntry.values("contactRevealed", false),
                AuditEntry.values("contactRevealed", true, "seekerId", seekerId, "tutorId", tutorId),
                null);
    }
}
