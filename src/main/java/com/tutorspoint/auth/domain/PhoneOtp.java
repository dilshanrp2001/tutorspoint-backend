package com.tutorspoint.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * The one-time code texted to a phone number at registration (FR-A2).
 *
 * <p>Six digits is a small search space, so the code is stored under a slow salted hash
 * (BCrypt) and is never looked up by its digest — the row is found by {@code userId} and
 * the candidate code is then compared. The attempt ceiling is what actually protects it:
 * five wrong guesses burn the code.
 *
 * <p>Every send costs money, so the service layer also caps sends per number per hour.
 */
@Entity
@Table(name = "phone_otps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhoneOtp extends SingleUseToken {

    /** Wrong guesses tolerated before the code is burnt and a new one must be sent. */
    public static final int MAX_VERIFICATION_ATTEMPTS = 5;

    private static final String ERROR_PREFIX = "OTP";

    /** BCrypt digest of the six-digit code. Sixty characters, sized like a password hash. */
    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    public PhoneOtp(Long userId, String codeHash, Instant expiresAt) {
        super(userId, expiresAt);
        if (codeHash == null || codeHash.isBlank()) {
            throw new IllegalArgumentException("codeHash must not be blank");
        }
        this.codeHash = codeHash;
    }

    @Override
    protected int maxAttempts() {
        return MAX_VERIFICATION_ATTEMPTS;
    }

    @Override
    protected String errorPrefix() {
        return ERROR_PREFIX;
    }
}
