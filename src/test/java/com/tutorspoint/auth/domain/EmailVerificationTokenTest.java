package com.tutorspoint.auth.domain;

import com.tutorspoint.common.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The emailed link obeys the same single-use rules as an OTP but reports them under its
 * own error keys, so the frontend can tell the user which secret went stale (FR-A3).
 */
class EmailVerificationTokenTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-09-11T10:00:00Z");
    private static final Instant EXPIRES_AT = ISSUED_AT.plus(Duration.ofHours(24));

    private static EmailVerificationToken token() {
        return new EmailVerificationToken(42L, "a".repeat(64), EXPIRES_AT);
    }

    @Test
    void aFreshTokenIsRedeemableUntilItExpires() {
        EmailVerificationToken token = token();

        assertThat(token.isRedeemable(ISSUED_AT)).isTrue();
        assertThat(token.isRedeemable(EXPIRES_AT)).isFalse();
    }

    @Test
    void consumingItOnceWorksAndTwiceDoesNot() {
        EmailVerificationToken token = token();
        Instant clicked = ISSUED_AT.plus(Duration.ofMinutes(3));

        token.consume(clicked);

        assertThat(token.getConsumedAt()).isEqualTo(clicked);
        assertRefusedWith("EMAIL_VERIFICATION_TOKEN_ALREADY_USED", token, clicked.plusSeconds(1));
    }

    @Test
    void aStaleLinkIsRefused() {
        assertRefusedWith("EMAIL_VERIFICATION_TOKEN_EXPIRED", token(), EXPIRES_AT.plusSeconds(1));
    }

    @Test
    void repeatedFailuresBurnTheToken() {
        EmailVerificationToken token = token();

        for (int attempt = 0; attempt < 5; attempt++) {
            token.recordFailedAttempt();
        }

        assertThat(token.attemptsExhausted()).isTrue();
        assertRefusedWith("EMAIL_VERIFICATION_TOKEN_ATTEMPTS_EXCEEDED", token, ISSUED_AT.plusSeconds(30));
    }

    @Test
    void aTokenAlwaysCarriesItsDigest() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmailVerificationToken(42L, null, EXPIRES_AT))
                .withMessageContaining("tokenHash");
    }

    private static void assertRefusedWith(String expectedCode, EmailVerificationToken token, Instant now) {
        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(() -> token.consume(now))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo(expectedCode));
    }
}
