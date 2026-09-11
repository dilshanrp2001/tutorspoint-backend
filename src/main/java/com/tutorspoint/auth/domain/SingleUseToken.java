package com.tutorspoint.auth.domain;

import com.tutorspoint.common.domain.BaseEntity;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;

/**
 * The rules every verification secret obeys: it expires, it may be redeemed once, and a
 * caller gets a limited number of tries before it is burnt. {@link EmailVerificationToken}
 * and {@link PhoneOtp} differ only in the secret they carry and how long a caller gets.
 *
 * <p>Holds {@code userId} rather than a {@link User} association on purpose: a token is
 * its own small aggregate with its own lifecycle, and nothing here needs to load an
 * account. The foreign key still exists in the database.
 *
 * <p>Time is passed in rather than read from the clock, so expiry is exercised by unit
 * tests without freezing a static.
 */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class SingleUseToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Null until redeemed. A non-null value makes the token spent forever. */
    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    protected SingleUseToken(Long userId, Instant expiresAt) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt must not be null");
        }
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.attemptCount = 0;
    }

    /** How many wrong guesses this kind of secret tolerates before it is burnt. */
    protected abstract int maxAttempts();

    /** Prefix for this token type's error keys, e.g. {@code OTP} gives {@code OTP_EXPIRED}. */
    protected abstract String errorPrefix();

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean attemptsExhausted() {
        return attemptCount >= maxAttempts();
    }

    /** True when the secret could still be redeemed right now. */
    public boolean isRedeemable(Instant now) {
        return !isConsumed() && !isExpired(now) && !attemptsExhausted();
    }

    /**
     * Counts a wrong guess. The comparison itself belongs to the service, which owns the
     * password encoder; the entity owns only what a failure means.
     *
     * @throws BusinessRuleViolationException if the secret was already redeemed
     */
    public void recordFailedAttempt() {
        if (isConsumed()) {
            throw new BusinessRuleViolationException(errorKey("ALREADY_USED"),
                    "This %s has already been used".formatted(describe()));
        }
        this.attemptCount++;
    }

    /**
     * Redeems the secret. Nothing may redeem it twice.
     *
     * @throws BusinessRuleViolationException if it is spent, expired, or out of attempts
     */
    public void consume(Instant now) {
        if (isConsumed()) {
            throw new BusinessRuleViolationException(errorKey("ALREADY_USED"),
                    "This %s has already been used".formatted(describe()));
        }
        if (isExpired(now)) {
            throw new BusinessRuleViolationException(errorKey("EXPIRED"),
                    "This %s expired at %s".formatted(describe(), expiresAt));
        }
        if (attemptsExhausted()) {
            throw new BusinessRuleViolationException(errorKey("ATTEMPTS_EXCEEDED"),
                    "Too many failed attempts on this %s".formatted(describe()));
        }
        this.consumedAt = now;
    }

    private String errorKey(String suffix) {
        return errorPrefix() + "_" + suffix;
    }

    private String describe() {
        return errorPrefix().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
