package com.tutorspoint.auth.domain;

/**
 * The lifecycle of an account. Transitions are owned by {@link User} — nothing outside
 * the entity may move an account between these states.
 *
 * <pre>
 *   PENDING_VERIFICATION --activate()--------------------> ACTIVE
 *   PENDING_VERIFICATION or ACTIVE --suspend()-----------> SUSPENDED
 *   SUSPENDED --reinstate(), both channels verified------> ACTIVE
 *   SUSPENDED --reinstate(), verification unfinished-----> PENDING_VERIFICATION
 *   any state --delete()---------------------------------> DELETED (terminal)
 * </pre>
 */
public enum AccountStatus {

    /** Registered, but email and/or phone are still unconfirmed. Cannot log in. */
    PENDING_VERIFICATION,

    /** Fully verified and usable. */
    ACTIVE,

    /** Blocked by an administrator. Reversible. */
    SUSPENDED,

    /** Soft-deleted at the user's request (FR-A7). Terminal: nothing reopens it. */
    DELETED
}
