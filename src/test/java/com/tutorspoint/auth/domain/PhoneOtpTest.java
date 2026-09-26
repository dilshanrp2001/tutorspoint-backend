package com.tutorspoint.auth.domain;

import com.tutorspoint.common.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Expiry, single use and the attempt ceiling that protects a six-digit code (FR-A2). */
class PhoneOtpTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-09-11T10:00:00Z");
    private static final Instant EXPIRES_AT = ISSUED_AT.plus(Duration.ofMinutes(5));

    private static PhoneOtp otp() {
        return new PhoneOtp(42L, "$2a$10$digest", EXPIRES_AT);
    }

    @Test
    void aFreshCodeIsRedeemable() {
        PhoneOtp otp = otp();

        assertThat(otp.isConsumed()).isFalse();
        assertThat(otp.getAttemptCount()).isZero();
        assertThat(otp.isRedeemable(ISSUED_AT)).isTrue();
    }

    @Test
    void expiryIsInclusiveOfTheExpiryInstant() {
        PhoneOtp otp = otp();

        assertThat(otp.isExpired(EXPIRES_AT.minusMillis(1))).isFalse();
        assertThat(otp.isExpired(EXPIRES_AT)).isTrue();
    }

    @Test
    void consumingStampsTheMomentItWasRedeemed() {
        PhoneOtp otp = otp();
        Instant redeemedAt = ISSUED_AT.plusSeconds(30);

        otp.consume(redeemedAt);

        assertThat(otp.isConsumed()).isTrue();
        assertThat(otp.getConsumedAt()).isEqualTo(redeemedAt);
        assertThat(otp.isRedeemable(redeemedAt)).isFalse();
    }

    @Test
    void aCodeCannotBeRedeemedTwice() {
        PhoneOtp otp = otp();
        otp.consume(ISSUED_AT.plusSeconds(30));

        assertRefusedWith("OTP_ALREADY_USED", otp, ISSUED_AT.plusSeconds(31));
    }

    @Test
    void anExpiredCodeIsRefused() {
        PhoneOtp otp = otp();

        assertRefusedWith("OTP_EXPIRED", otp, EXPIRES_AT.plusSeconds(1));
        assertThat(otp.isConsumed()).isFalse();
    }

    @Test
    void fiveWrongGuessesBurnTheCode() {
        PhoneOtp otp = otp();

        for (int attempt = 0; attempt < PhoneOtp.MAX_VERIFICATION_ATTEMPTS; attempt++) {
            assertThat(otp.attemptsExhausted()).isFalse();
            otp.recordFailedAttempt();
        }

        assertThat(otp.getAttemptCount()).isEqualTo(PhoneOtp.MAX_VERIFICATION_ATTEMPTS);
        assertThat(otp.attemptsExhausted()).isTrue();
        assertRefusedWith("OTP_ATTEMPTS_EXCEEDED", otp, ISSUED_AT.plusSeconds(30));
    }

    @Test
    void aRedeemedCodeCannotAccrueFurtherAttempts() {
        PhoneOtp otp = otp();
        otp.consume(ISSUED_AT.plusSeconds(30));

        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(otp::recordFailedAttempt)
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("OTP_ALREADY_USED"));
    }

    @Test
    void aCodeIsAlwaysTiedToAUserAndAnExpiry() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PhoneOtp(null, "$2a$10$digest", EXPIRES_AT))
                .withMessageContaining("userId");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PhoneOtp(42L, "$2a$10$digest", null))
                .withMessageContaining("expiresAt");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PhoneOtp(42L, " ", EXPIRES_AT))
                .withMessageContaining("codeHash");
    }

    private static void assertRefusedWith(String expectedCode, PhoneOtp otp, Instant now) {
        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(() -> otp.consume(now))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo(expectedCode));
    }
}
