package com.tutorspoint.auth.domain;

/**
 * What a user is on the platform. Each role has its own {@link User} subtype, and the
 * subtype's constructor is the only thing that sets this — the two can never disagree.
 *
 * <p>An unauthenticated visitor is not a role: no row exists for them.
 */
public enum Role {

    TUTOR,
    PARENT,
    STUDENT,
    ADMIN
}
