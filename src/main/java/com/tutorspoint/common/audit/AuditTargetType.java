package com.tutorspoint.common.audit;

/** What kind of row an audited action was taken on. Paired with an id, never a foreign key. */
public enum AuditTargetType {

    USER,
    TUTOR_PROFILE,
    VERIFICATION_DOCUMENT,
    ENQUIRY
}
