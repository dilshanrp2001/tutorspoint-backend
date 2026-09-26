package com.tutorspoint.auth.dto;

/**
 * The token pair returned by login and refresh (FR-A4).
 *
 * <p>The access token goes on every request and expires quickly. The refresh token is sent
 * only to /api/auth/refresh and /api/auth/logout, and is rotated on every use — the value
 * returned here replaces the one that was presented.
 */
public record AuthTokensResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds) {

    private static final String BEARER = "Bearer";

    public static AuthTokensResponse bearer(String accessToken, String refreshToken, long expiresInSeconds) {
        return new AuthTokensResponse(accessToken, refreshToken, BEARER, expiresInSeconds);
    }
}
