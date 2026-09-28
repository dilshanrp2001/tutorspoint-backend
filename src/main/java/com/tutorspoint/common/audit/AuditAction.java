package com.tutorspoint.common.audit;

/**
 * Everything NFR-10 requires a durable record of: verification decisions, moderation, and the
 * moment a thread's contact details are revealed.
 *
 * <p>A closed set on purpose. An audit trail that accepts any string drifts into a log file
 * nobody can query; adding an action here is a reviewed change, and so is the CHECK in the
 * migration that mirrors it.
 */
public enum AuditAction {

    /** An administrator opened a tutor's submitted document. Reading an NIC leaves a trace. */
    DOCUMENT_VIEWED,
    DOCUMENT_APPROVED,
    DOCUMENT_REJECTED,

    /** The verified badge was granted (FR-R3). */
    TUTOR_VERIFIED,
    TUTOR_VERIFICATION_REVOKED,

    PROFILE_SUSPENDED,
    PROFILE_REINSTATED,

    ACCOUNT_SUSPENDED,
    ACCOUNT_REINSTATED,

    /** A tutor's first reply opened the contact channel on an enquiry (FR-E2). */
    CONTACT_REVEALED
}
