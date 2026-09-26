package com.tutorspoint.auth;

import com.tutorspoint.auth.domain.RefreshToken;
import com.tutorspoint.common.exception.AuthenticationFailedException;

/**
 * Sign-in sessions: opening one, validating one, and ending one or all of them (FR-A4).
 *
 * <p>Its own service rather than part of {@link AuthService} because it is not only the auth
 * flows that end sessions — resetting a password and deleting an account must both sign the
 * user out everywhere, and neither of those is a login concern.
 */
public interface RefreshTokenService {

    /**
     * Opens a session and returns the refresh token to hand to the client. Only the digest
     * is stored, so this is the one and only time the value exists.
     */
    String issueFor(Long userId);

    /**
     * The live session a refresh token identifies.
     *
     * @throws AuthenticationFailedException if the token is unknown, expired or already
     *         revoked. A revoked token being presented again means it leaked or the client
     *         replayed it, so every session for that user is ended as well.
     */
    RefreshToken requireActive(String refreshToken);

    /** Ends one session, which is both what logout does and what rotation does. */
    void revoke(RefreshToken session);

    /**
     * Ends the session a token identifies, if there is one. Logout is idempotent: a client
     * signing out with a token that is already dead has got what it wanted.
     */
    void revokeIfPresent(String refreshToken);

    /** Ends every live session for an account: password reset, deletion, suspected theft. */
    void revokeAllFor(Long userId);
}
