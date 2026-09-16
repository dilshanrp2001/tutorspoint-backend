package com.tutorspoint.enquiry.event;

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
 * @param enquiryId      the thread, for the deep link the parent follows
 * @param parentId       who is being answered
 * @param parentEmail    where the notification goes
 * @param parentName     for the greeting
 * @param parentLanguage the language the parent reads
 * @param tutorId        who answered, for the analytics counters
 * @param tutorName      the name of the tutor who replied
 */
public record EnquiryRespondedEvent(
        Long enquiryId,
        Long parentId,
        String parentEmail,
        String parentName,
        Language parentLanguage,
        Long tutorId,
        String tutorName) {
}
