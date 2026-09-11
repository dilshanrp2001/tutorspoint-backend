package com.tutorspoint.auth.domain;

import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The account state machine. Exercised through {@link Tutor} because {@link User} is
 * abstract — every subtype inherits exactly these rules.
 */
class UserTest {

    private static final Instant NOW = Instant.parse("2026-09-11T10:15:30Z");

    private static Tutor newTutor() {
        return new Tutor("Nimali@Example.COM", "$2a$10$hash", "Nimali Perera", "+94771234567", Language.SI);
    }

    private static Tutor verifiedTutor() {
        Tutor tutor = newTutor();
        tutor.verifyEmail();
        tutor.verifyPhone();
        return tutor;
    }

    @Nested
    @DisplayName("registration")
    class Registration {

        @Test
        void startsPendingVerificationWithNeitherChannelConfirmed() {
            Tutor tutor = newTutor();

            assertThat(tutor.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
            assertThat(tutor.isEmailVerified()).isFalse();
            assertThat(tutor.isPhoneVerified()).isFalse();
            assertThat(tutor.isFullyVerified()).isFalse();
            assertThat(tutor.getLastLoginAt()).isNull();
        }

        @Test
        void takesItsRoleFromTheSubtype() {
            assertThat(newTutor().getRole()).isEqualTo(Role.TUTOR);
            assertThat(newParent().getRole()).isEqualTo(Role.PARENT);
            assertThat(newAdmin().getRole()).isEqualTo(Role.ADMIN);
        }

        @Test
        void lowerCasesTheEmailSoTheUniqueIndexIsCaseInsensitive() {
            assertThat(newTutor().getEmail()).isEqualTo("nimali@example.com");
        }

        @Test
        void rejectsMissingIdentifiers() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new Tutor("  ", "$2a$10$hash", "Nimali", "+94771234567", Language.EN))
                    .withMessageContaining("email");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new Tutor("a@b.lk", "$2a$10$hash", "Nimali", "+94771234567", null))
                    .withMessageContaining("preferredLanguage");
        }
    }

    @Nested
    @DisplayName("activate()")
    class Activate {

        @Test
        void isRefusedWhileNeitherChannelIsVerified() {
            assertVerificationIncomplete(newTutor());
        }

        @Test
        void isRefusedWhileOnlyTheEmailIsVerified() {
            Tutor tutor = newTutor();
            tutor.verifyEmail();

            assertVerificationIncomplete(tutor);
        }

        @Test
        void isRefusedWhileOnlyThePhoneIsVerified() {
            Tutor tutor = newTutor();
            tutor.verifyPhone();

            assertVerificationIncomplete(tutor);
        }

        @Test
        void succeedsOnceBothChannelsAreVerified() {
            Tutor tutor = verifiedTutor();

            tutor.activate();

            assertThat(tutor.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        void reinstatesASuspendedAccount() {
            Tutor tutor = verifiedTutor();
            tutor.activate();
            tutor.suspend();

            tutor.activate();

            assertThat(tutor.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        }

        private void assertVerificationIncomplete(Tutor tutor) {
            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(tutor::activate)
                    .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ACCOUNT_VERIFICATION_INCOMPLETE"));
            assertThat(tutor.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        }
    }

    @Nested
    @DisplayName("verification")
    class Verification {

        @Test
        void isIdempotentBecauseUsersClickTwice() {
            Tutor tutor = newTutor();

            tutor.verifyEmail();
            tutor.verifyEmail();
            tutor.verifyPhone();
            tutor.verifyPhone();

            assertThat(tutor.isFullyVerified()).isTrue();
        }

        @Test
        void doesNotActivateOnItsOwn() {
            Tutor tutor = verifiedTutor();

            assertThat(tutor.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        }
    }

    @Nested
    @DisplayName("suspend() and delete()")
    class SuspendAndDelete {

        @Test
        void suspensionBlocksTheAccountWithoutLosingVerification() {
            Tutor tutor = verifiedTutor();
            tutor.activate();

            tutor.suspend();

            assertThat(tutor.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
            assertThat(tutor.isFullyVerified()).isTrue();
        }

        @Test
        void deletionIsTerminal() {
            Tutor tutor = verifiedTutor();
            tutor.activate();

            tutor.delete();

            assertThat(tutor.getStatus()).isEqualTo(AccountStatus.DELETED);
            assertDeletedRefuses(tutor::activate);
            assertDeletedRefuses(tutor::suspend);
            assertDeletedRefuses(tutor::verifyEmail);
            assertDeletedRefuses(tutor::verifyPhone);
            assertDeletedRefuses(() -> tutor.changePassword("$2a$10$other"));
            assertDeletedRefuses(() -> tutor.changePreferredLanguage(Language.TA));
        }

        @Test
        void deletionIsIdempotent() {
            Tutor tutor = newTutor();

            tutor.delete();
            tutor.delete();

            assertThat(tutor.getStatus()).isEqualTo(AccountStatus.DELETED);
        }

        private void assertDeletedRefuses(ThrowingCallable call) {
            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(call)
                    .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ACCOUNT_DELETED"));
        }
    }

    @Nested
    @DisplayName("recordLogin()")
    class RecordLogin {

        @Test
        void stampsTheMomentOfAnAcceptedSignIn() {
            Tutor tutor = verifiedTutor();
            tutor.activate();

            tutor.recordLogin(NOW);

            assertThat(tutor.getLastLoginAt()).isEqualTo(NOW);
        }

        @Test
        void isRefusedForAnAccountThatIsNotActive() {
            assertRefused(newTutor());

            Tutor suspended = verifiedTutor();
            suspended.activate();
            suspended.suspend();
            assertRefused(suspended);

            Tutor deleted = verifiedTutor();
            deleted.delete();
            assertRefused(deleted);
        }

        private void assertRefused(Tutor tutor) {
            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(() -> tutor.recordLogin(NOW))
                    .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ACCOUNT_NOT_ACTIVE"));
            assertThat(tutor.getLastLoginAt()).isNull();
        }
    }

    @Nested
    @DisplayName("account details")
    class AccountDetails {

        @Test
        void passwordIsReplacedByDigestOnly() {
            Tutor tutor = newTutor();

            tutor.changePassword("$2a$10$replacement");

            assertThat(tutor.getPasswordHash()).isEqualTo("$2a$10$replacement");
            assertThatIllegalArgumentException().isThrownBy(() -> tutor.changePassword(" "));
        }

        @Test
        void preferredLanguageIsChangeable() {
            Tutor tutor = newTutor();

            tutor.changePreferredLanguage(Language.TA);

            assertThat(tutor.getPreferredLanguage()).isEqualTo(Language.TA);
        }
    }

    private static Parent newParent() {
        return new Parent("kamal@example.lk", "$2a$10$hash", "Kamal Silva", "+94712223334", Language.TA);
    }

    private static Admin newAdmin() {
        return new Admin("ops@tutorspoint.lk", "$2a$10$hash", "Ops", "+94700000000", Language.EN);
    }
}
