package com.tutorspoint.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * The secret behind the "confirm your email" link (FR-A3).
 *
 * <p>Only a digest is stored, so a leaked database does not hand out working links. The
 * digest must be deterministic (SHA-256 of a high-entropy random token) because the link
 * carries no user id — the token itself is the lookup key. That is safe here precisely
 * because the token is long and random; a short, guessable secret like an OTP is treated
 * differently, see {@link PhoneOtp}.
 */
@Entity
@Table(name = "email_verification_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerificationToken extends SingleUseToken {

    /** Generous: a mis-typed or truncated link should not burn the token immediately. */
    private static final int MAX_ATTEMPTS = 5;

    private static final String ERROR_PREFIX = "EMAIL_VERIFICATION_TOKEN";

    /** SHA-256 of the token in the emailed link, hex-encoded: always 64 characters. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    public EmailVerificationToken(Long userId, String tokenHash, Instant expiresAt) {
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
