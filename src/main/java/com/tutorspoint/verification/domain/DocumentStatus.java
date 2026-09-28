package com.tutorspoint.verification.domain;

/**
 * Where a submitted document is in review (FR-T7).
 *
 * <p>The tutor may withdraw a document only while it is {@link #PENDING}: once a reviewer has
 * ruled on it, the decision and what it was made about are an audit record, and deleting the
 * evidence behind an approval would leave a verified badge nobody can account for.
 */
public enum DocumentStatus {

    /** Submitted, waiting for a reviewer. */
    PENDING,

    APPROVED,

    /** Refused, with a reason the tutor can act on. */
    REJECTED
}
