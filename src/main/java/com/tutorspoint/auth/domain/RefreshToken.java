package com.tutorspoint.auth.domain;

import com.tutorspoint.common.domain.BaseEntity;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A long-lived, revocable sign-in session (FR-A4).
 *
 * <p>Access tokens are short-lived and stateless, so nothing can cancel one; this row is
 * what makes a session revocable. Logging out, rotating on refresh, resetting a password
 * and deleting an account all work by revoking rows here.
 *
 * <p>Not a {@link SingleUseToken}: there is nothing to guess and therefore no attempt
 * ceiling. A refresh token is 256 bits of randomness looked up by its SHA-256 digest —
 * either the caller holds the exact value or they hold nothing.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

    private static final String ERROR_ALREADY_REVOKED = "REFRESH_TOKEN_ALREADY_REVOKED";

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** SHA-256 of the token the client holds, hex-encoded: always 64 characters. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Null while the session is live. Once set, the session is over for good. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    public RefreshToken(Long userId, String tokenHash, Instant expiresAt) {
        this.userId = require(userId, "userId");
        this.tokenHash = requireText(tokenHash, "tokenHash");
        this.expiresAt = require(expiresAt, "expiresAt");
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** True when this token may still be exchanged for a new pair. */
    public boolean isActive(Instant now) {
        return !isRevoked() && !isExpired(now);
    }

    /**
     * Ends the session. Rotation, logout and "sign out everywhere" all land here.
     *
     * @throws BusinessRuleViolationException if the session was already revoked — a
     *                                        second presentation of a spent refresh token
     *                                        is a replay, not a no-op, and the caller
     *                                        needs to be able to react to it
     */
    public void revoke(Instant now) {
        if (isRevoked()) {
            throw new BusinessRuleViolationException(ERROR_ALREADY_REVOKED,
                    "Refresh token %s was already revoked at %s".formatted(getId(), revokedAt));
        }
        this.revokedAt = require(now, "now");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static <T> T require(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }
}
