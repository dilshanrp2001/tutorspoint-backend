package com.tutorspoint.auth.security;

import com.tutorspoint.common.exception.AuthenticationFailedException;
import com.tutorspoint.common.exception.UnauthorizedActionException;

/**
 * The caller of the current request, and the single place the ownership rule is written.
 *
 * <p>Every service that touches a user-owned resource needs the same two questions
 * answered: who is calling, and is this theirs. Repeating the owner comparison in each of
 * them is how an authorization hole eventually arrives — the day someone forgets it the
 * check is simply absent and nothing fails. Here it is one named call that cannot be
 * half-written, and route rules plus {@code @PreAuthorize} sit in front of it.
 *
 * <p>An interface because the implementation reads Spring Security's thread-local context:
 * services depend on this contract, and a unit test supplies a caller without standing up
 * a security context.
 */
public interface CurrentUser {

    /**
     * The authenticated caller.
     *
     * @throws AuthenticationFailedException if the request carries no authenticated
     *                                       principal (401)
     */
    AuthenticatedUser require();

    /** Shorthand for the caller's id, which is what most callers actually want. */
    default Long requireId() {
        return require().userId();
    }

    /**
     * Asserts the resource belongs to the caller.
     *
     * @throws UnauthorizedActionException if it belongs to somebody else (403)
     */
    default void requireOwnership(Long ownerId) {
        AuthenticatedUser caller = require();
        if (!caller.owns(ownerId)) {
            throw new UnauthorizedActionException(
                    "Account %s may not act on a resource owned by %s"
                            .formatted(caller.userId(), ownerId));
        }
    }
}
