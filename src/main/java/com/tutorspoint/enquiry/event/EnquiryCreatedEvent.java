package com.tutorspoint.enquiry.event;

import com.tutorspoint.common.domain.Language;

/**
 * A parent has opened a thread with a tutor, and the row is committed.
 *
 * <p>Published by the enquiry service through {@code ApplicationEventPublisher}, which is the
 * whole point: the service knows an enquiry was created and nothing else. It does not know
 * that a tutor gets an email about it, that a counter is incremented, or that a third thing
 * will be added in Phase 5 — and so none of those can break it. The listeners are in this
 * package next to the event so the set of consequences is readable in one place, without the
 * publisher having to name any of them (Observer, architecture section 7).
 *
 * <p><strong>Self-contained by design.</strong> The event carries the values its listeners
 * need rather than an id for them to load. Listeners run after the transaction has committed,
 * on a pool thread, where a lazy association would have no session to load from; carrying the
 * data avoids that class of failure entirely. It also means the notification path never has to
 * re-read a user row to find an email address.
 *
 * @param enquiryId     the thread, for the deep link the tutor follows
 * @param parentId      who asked, for the analytics counters
 * @param parentName    how the tutor sees them — a name, never a contact detail
 * @param tutorId       who was asked
 * @param tutorEmail    where the notification goes. A notification is not a reveal: the
 *                      platform may write to a tutor at any time, and this address never
 *                      reaches the parent.
 * @param tutorName     for the greeting
 * @param tutorLanguage the language the tutor reads
 * @param subjectName   what the enquiry is about, already rendered in {@code tutorLanguage}
 */
public record EnquiryCreatedEvent(
        Long enquiryId,
        Long parentId,
        String parentName,
        Long tutorId,
        String tutorEmail,
        String tutorName,
        Language tutorLanguage,
        String subjectName) {
}
