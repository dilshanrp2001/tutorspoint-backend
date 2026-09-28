package com.tutorspoint.enquiry.domain;

/**
 * Where a conversation has got to. Transitions are owned by {@link Enquiry} — nothing
 * outside the entity moves a thread between these states.
 *
 * <pre>
 *   SENT --markViewed()--> VIEWED --reply(tutor)--> RESPONDED
 *     \                      |                         |
 *      \---------------------+------ close() ----------+--> CLOSED
 *
 *   SENT/VIEWED ---- markAsSpam() ----> SPAM (terminal)
 * </pre>
 *
 * <p>{@link #RESPONDED} is the one that matters commercially: it is the moment contact
 * details become visible (FR-E2, NFR-5), and the denominator of the enquiry-to-response
 * rate the pilot is measured on (OBJ-6).
 */
public enum EnquiryStatus {

    /** Delivered to the tutor's inbox, not yet opened. */
    SENT,

    /** The tutor has opened the thread but not answered. */
    VIEWED,

    /** The tutor has replied at least once. Contact details are revealed from here on. */
    RESPONDED,

    /** Either side considers the conversation finished. No further messages. */
    CLOSED,

    /**
     * Reported or moderated as abuse. Terminal, and deliberately not the same as CLOSED:
     * a closed thread is a normal outcome and counts towards the response rate, a spam
     * thread is neither.
     */
    SPAM
}
