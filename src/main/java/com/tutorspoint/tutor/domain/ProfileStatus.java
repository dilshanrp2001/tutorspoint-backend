package com.tutorspoint.tutor.domain;

/**
 * Where a tutor profile is in its lifecycle (FR-T8).
 *
 * <p>Only {@link #PUBLISHED} is visible to anybody but the owner and an administrator, and
 * that is the whole point of the enum: search, the public profile endpoint and the card list
 * all filter on this one column rather than each re-deciding what "live" means.
 */
public enum ProfileStatus {

    /** Being filled in. The default from the moment the profile row exists. */
    DRAFT,

    /** Live and discoverable. Reachable only through a completeness check. */
    PUBLISHED,

    /** Was live, taken down by the tutor. Distinct from {@link #DRAFT}: the content survives. */
    UNPUBLISHED,

    /** Taken down by an administrator. The tutor cannot publish out of it. */
    SUSPENDED
}
