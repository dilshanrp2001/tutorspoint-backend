package com.tutorspoint.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * The secret behind the "choose a new password" link (FR-A6).
 *
 * <p>Shaped exactly like {@link EmailVerificationToken} — a long random value stored as a
 * deterministic SHA-256 digest, because the link carries nothing but the token — and it
 * obeys the same single-use, expiring, attempt-capped rules from {@link SingleUseToken}.
 * It is a separate entity rather than a flag on that one because redeeming it does
 * something entirely different, and the two must never be interchangeable: an email
 * verification link must not be able to set a password.
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken extends SingleUseToken {

    private static final int MAX_ATTEMPTS = 5;

    private static final String ERROR_PREFIX = "PASSWORD_RESET_TOKEN";

    /** SHA-256 of the token in the emailed link, hex-encoded: always 64 characters. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    public PasswordResetToken(Long userId, String tokenHash, Instant expiresAt) {
        super(userId, expiresAt);
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("tokenHash must not be blank");
        }
        this.tokenHash = tokenHash;
    }

    @Override
    protected int maxAttempts() {
        return MAX_ATTEMPTS;
    }

    @Override
    protected String errorPrefix() {
        return ERROR_PREFIX;
    }
}
