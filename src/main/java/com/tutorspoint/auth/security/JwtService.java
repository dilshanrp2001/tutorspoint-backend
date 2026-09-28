package com.tutorspoint.auth.security;

import com.tutorspoint.auth.domain.User;
import com.tutorspoint.common.exception.AuthenticationFailedException;

import java.time.Duration;

/**
 * Issues and verifies the stateless access token (FR-A4).
 *
 * <p>An interface so the rest of the module never sees a JWT library type: the token
 * format is an implementation detail behind these three calls, and the vendor's API is
 * confined to one adapter, as every external dependency in this codebase is.
 */
public interface JwtService {

    /**
     * A signed access token identifying this account. Short-lived by configuration —
     * nothing can revoke one before it expires, which is why the refresh token exists.
     */
    String issueAccessToken(User user);

    /**
     * The caller a token identifies.
     *
     * @throws AuthenticationFailedException if the token is malformed, unsigned, signed
     *                                       with the wrong key, issued elsewhere or expired
     */
    AuthenticatedUser parse(String accessToken);

    /** How long an issued token lasts, so the API can tell the client when to refresh. */
    Duration accessTokenTtl();
}
