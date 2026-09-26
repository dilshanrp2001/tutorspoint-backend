package com.tutorspoint.auth;

import com.tutorspoint.auth.config.AuthProperties;
import com.tutorspoint.auth.config.OtpDelivery;
import com.tutorspoint.auth.domain.AccountStatus;
import com.tutorspoint.auth.domain.EmailVerificationToken;
import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.PasswordResetToken;
import com.tutorspoint.auth.domain.PhoneOtp;
import com.tutorspoint.auth.domain.RefreshToken;
import com.tutorspoint.auth.domain.Role;
import com.tutorspoint.auth.domain.Student;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.domain.User;
import com.tutorspoint.auth.dto.ForgotPasswordRequest;
import com.tutorspoint.auth.dto.LoginRequest;
import com.tutorspoint.auth.dto.RefreshTokenRequest;
import com.tutorspoint.auth.dto.RegisterRequest;
import com.tutorspoint.auth.dto.RegistrableRole;
import com.tutorspoint.auth.dto.ResetPasswordRequest;
import com.tutorspoint.auth.dto.SendOtpRequest;
import com.tutorspoint.auth.dto.VerifyEmailRequest;
import com.tutorspoint.auth.dto.VerifyOtpRequest;
import com.tutorspoint.auth.repository.EmailVerificationTokenRepository;
import com.tutorspoint.auth.repository.PasswordResetTokenRepository;
import com.tutorspoint.auth.repository.PhoneOtpRepository;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.JwtService;
import com.tutorspoint.auth.security.SecureTokens;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.AuthenticationFailedException;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.notification.NotificationService;
import com.tutorspoint.notification.domain.Notification;
import com.tutorspoint.notification.domain.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * The auth state machine and every way it is allowed to refuse.
 *
 * <p>Unit tests with a fixed clock: expiry, the hourly send cap and the attempt ceiling are
 * all time-dependent, and none of them is worth sleeping for. No Spring context — what is
 * under test is the decision-making, not the wiring.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-09-12T09:00:00Z");

    private static final String EMAIL = "nimali@example.lk";
    private static final String PHONE = "+94771234567";
    private static final String PASSWORD = "Colombo2026";
    private static final String PASSWORD_HASH = "$2a$10$storedhash";
    private static final String RAW_LINK_TOKEN = "a-long-random-link-token";
    private static final String OTP_CODE = "004271";

    @Mock
    private UserRepository users;

    @Mock
    private EmailVerificationTokenRepository emailTokens;

    @Mock
    private PhoneOtpRepository phoneOtps;

    @Mock
    private PasswordResetTokenRepository passwordResetTokens;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtService jwtService;

    @Mock
    private NotificationService notifications;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccountMapper accountMapper;

    @Captor
    private ArgumentCaptor<Notification> sent;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = serviceDelivering(OtpDelivery.SMS);
    }

    private static AuthProperties propertiesDelivering(OtpDelivery otpDelivery) {
        return propertiesWithPhoneVerification(otpDelivery, true);
    }

    private static AuthProperties propertiesWithPhoneVerification(
            OtpDelivery otpDelivery, boolean phoneVerificationEnabled) {
        return new AuthProperties(
                Duration.ofHours(24),
                Duration.ofMinutes(30),
                Duration.ofMinutes(5),
                phoneVerificationEnabled,
                otpDelivery,
                3,
                "https://tutorspoint.test/verify-email",
                "https://tutorspoint.test/reset-password");
    }

    /** The same service, differing only in which transport carries the OTP. */
    private AuthServiceImpl serviceDelivering(OtpDelivery otpDelivery) {
        return serviceWith(propertiesDelivering(otpDelivery));
    }

    private AuthServiceImpl serviceWith(AuthProperties properties) {
        return new AuthServiceImpl(users, emailTokens, phoneOtps, passwordResetTokens,
                refreshTokenService, jwtService, notifications, passwordEncoder, accountMapper,
                properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("registration (FR-A1)")
    class Registration {

        @Test
        void createsAPendingTutorAndSendsOnlyTheVerificationLink() {
            givenNothingIsTaken();
            givenPasswordsAreHashed();
            given(users.saveAndFlush(any(User.class))).willAnswer(call -> withId(call.getArgument(0, User.class), 7L));

            service.register(registerAs(RegistrableRole.TUTOR));

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            then(users).should().saveAndFlush(saved.capture());
            assertThat(saved.getValue()).isInstanceOf(Tutor.class);
            assertThat(saved.getValue().getRole()).isEqualTo(Role.TUTOR);
            assertThat(saved.getValue().getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
            assertThat(saved.getValue().getPasswordHash()).isEqualTo(hashOf(PASSWORD));

            then(emailTokens).should().save(any(EmailVerificationToken.class));
            // Signup sends one message. No OTP is minted, so nothing reaches the same
            // inbox twice while phone verification is off.
            then(phoneOtps).shouldHaveNoInteractions();
            then(notifications).should().send(sent.capture());
            assertThat(sent.getAllValues())
                    .extracting(Notification::getType)
                    .containsExactly(NotificationType.EMAIL_VERIFICATION);
        }

        @Test
        void createsAParentForTheParentRole() {
            givenNothingIsTaken();
            givenPasswordsAreHashed();
            given(users.saveAndFlush(any(User.class))).willAnswer(call -> withId(call.getArgument(0, User.class), 8L));

            service.register(registerAs(RegistrableRole.PARENT));

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            then(users).should().saveAndFlush(saved.capture());
            assertThat(saved.getValue()).isInstanceOf(Parent.class);
            assertThat(saved.getValue().getRole()).isEqualTo(Role.PARENT);
        }

        @Test
        void createsAStudentForTheStudentRole() {
            givenNothingIsTaken();
            givenPasswordsAreHashed();
            given(users.saveAndFlush(any(User.class))).willAnswer(call -> withId(call.getArgument(0, User.class), 9L));

            service.register(registerAs(RegistrableRole.STUDENT));

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            then(users).should().saveAndFlush(saved.capture());
            assertThat(saved.getValue()).isInstanceOf(Student.class);
            assertThat(saved.getValue().getRole()).isEqualTo(Role.STUDENT);
        }

        @Test
        void sendsTheLinkToTheAddressAndNothingElse() {
            givenNothingIsTaken();
            givenPasswordsAreHashed();
            given(users.saveAndFlush(any(User.class))).willAnswer(call -> withId(call.getArgument(0, User.class), 7L));

            service.register(registerAs(RegistrableRole.TUTOR));

            then(notifications).should().send(sent.capture());
            Notification email = sent.getValue();

            assertThat(email.getRecipient()).isEqualTo(EMAIL);
            assertThat(email.getLanguage()).isEqualTo(Language.SI);
            assertThat((String) email.getVariables().get("verificationUrl"))
                    .startsWith("https://tutorspoint.test/verify-email?token=");
            assertThat(email.getVariables()).containsEntry("expiryHours", 24L);
        }

        @Test
        void refusesAnEmailThatAlreadyHasAnAccount() {
            given(users.existsByEmail(EMAIL)).willReturn(true);

            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(() -> service.register(registerAs(RegistrableRole.TUTOR)))
                    .satisfies(e -> assertThat(e.getCode()).isEqualTo("EMAIL_ALREADY_REGISTERED"));

            then(users).should(never()).saveAndFlush(any(User.class));
            then(notifications).shouldHaveNoInteractions();
        }

        @Test
        void refusesAPhoneNumberThatAlreadyHasAnAccount() {
            given(users.existsByEmail(EMAIL)).willReturn(false);
            given(users.existsByPhoneNumber(PHONE)).willReturn(true);

            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(() -> service.register(registerAs(RegistrableRole.TUTOR)))
                    .satisfies(e -> assertThat(e.getCode()).isEqualTo("PHONE_ALREADY_REGISTERED"));

            then(users).should(never()).saveAndFlush(any(User.class));
        }

        /**
         * Both the submitted password and the freshly generated OTP go through the encoder,
         * and the code is random, so the stub answers for whatever it is handed — which also
         * lets the assertions prove the stored hash came from the submitted password.
         */
        private void givenPasswordsAreHashed() {
            given(passwordEncoder.encode(anyString())).willAnswer(call -> hashOf(call.getArgument(0)));
        }

        private void givenNothingIsTaken() {
            given(users.existsByEmail(EMAIL)).willReturn(false);
            given(users.existsByPhoneNumber(PHONE)).willReturn(false);
        }
    }

    @Nested
    @DisplayName("email verification (FR-A3)")
    class EmailVerification {

        @Test
        void confirmsTheAddressAndActivatesTheAccount() {
            Tutor user = tutor(7L);
            givenLinkToken(emailToken(user.getId(), NOW.plus(Duration.ofHours(24))));
            given(users.findById(7L)).willReturn(Optional.of(user));

            service.verifyEmail(new VerifyEmailRequest(RAW_LINK_TOKEN));

            assertThat(user.isEmailVerified()).isTrue();
            // The link is the only challenge while phone verification is off, so opening
            // it is enough to finish signup on its own.
            assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(user.isPhoneVerified()).isFalse();
        }

        @Test
        void activatesTheAccountWhenThePhoneIsAlreadyVerified() {
            Tutor user = tutor(7L);
            user.verifyPhone();
            givenLinkToken(emailToken(user.getId(), NOW.plus(Duration.ofHours(24))));
            given(users.findById(7L)).willReturn(Optional.of(user));

            service.verifyEmail(new VerifyEmailRequest(RAW_LINK_TOKEN));

            assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        void refusesAnUnknownToken() {
            given(emailTokens.findByTokenHash(SecureTokens.sha256Hex(RAW_LINK_TOKEN)))
                    .willReturn(Optional.empty());

            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(() -> service.verifyEmail(new VerifyEmailRequest(RAW_LINK_TOKEN)))
                    .satisfies(e -> assertThat(e.getCode())
                            .isEqualTo("EMAIL_VERIFICATION_TOKEN_INVALID"));
        }

        @Test
        void refusesAnExpiredTokenAndSaysSo() {
            givenLinkToken(emailToken(7L, NOW.minusSeconds(1)));

            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(() -> service.verifyEmail(new VerifyEmailRequest(RAW_LINK_TOKEN)))
                    .satisfies(e -> assertThat(e.getCode())
                            .isEqualTo("EMAIL_VERIFICATION_TOKEN_EXPIRED"));

            then(users).shouldHaveNoInteractions();
        }

        @Test
        void refusesATokenThatHasAlreadyBeenUsed() {
            EmailVerificationToken token = emailToken(7L, NOW.plus(Duration.ofHours(1)));
            token.consume(NOW.minusSeconds(60));
            givenLinkToken(token);

            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(() -> service.verifyEmail(new VerifyEmailRequest(RAW_LINK_TOKEN)))
                    .satisfies(e -> assertThat(e.getCode())
                            .isEqualTo("EMAIL_VERIFICATION_TOKEN_ALREADY_USED"));
        }

        private void givenLinkToken(EmailVerificationToken token) {
            given(emailTokens.findByTokenHash(SecureTokens.sha256Hex(RAW_LINK_TOKEN)))
                    .willReturn(Optional.of(token));
        }
    }

    @Nested
    @DisplayName("phone verification (FR-A2)")
    class PhoneVerification {

        @Test
        void activatesTheAccountWhenTheEmailIsAlreadyVerified() {
            Tutor user = tutor(7L);
            user.verifyEmail();
            PhoneOtp otp = liveOtp(user.getId());
            given(users.findByEmail(EMAIL)).willReturn(Optional.of(user));
            given(phoneOtps.findFirstByUserIdOrderByCreatedAtDesc(7L)).willReturn(Optional.of(otp));
            given(passwordEncoder.matches(OTP_CODE, otp.getCodeHash())).willReturn(true);

            service.verifyOtp(new VerifyOtpRequest(EMAIL, OTP_CODE));

            assertThat(user.isPhoneVerified()).isTrue();
            assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(otp.isConsumed()).isTrue();
        }

        @Test
        void aWrongCodeCountsAnAttemptAndVerifiesNothing() {
            Tutor user = tutor(7L);
            PhoneOtp otp = liveOtp(user.getId());
            given(users.findByEmail(EMAIL)).willReturn(Optional.of(user));
            given(phoneOtps.findFirstByUserIdOrderByCreatedAtDesc(7L)).willReturn(Optional.of(otp));
            given(passwordEncoder.matches("111111", otp.getCodeHash())).willReturn(false);

            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(() -> service.verifyOtp(new VerifyOtpRequest(EMAIL, "111111")))
                    .satisfies(e -> assertThat(e.getCode()).isEqualTo("OTP_INVALID"));

            assertThat(otp.getAttemptCount()).isOne();
            assertThat(otp.isConsumed()).isFalse();
            assertThat(user.isPhoneVerified()).isFalse();
        }

        @Test
        void theSixthWrongGuessIsRefusedBeforeItIsEvenCompared() {
            Tutor user = tutor(7L);
            PhoneOtp otp = liveOtp(user.getId());
            for (int i = 0; i < PhoneOtp.MAX_VERIFICATION_ATTEMPTS; i++) {
                otp.recordFailedAttempt();
            }
            given(users.findByEmail(EMAIL)).willReturn(Optional.of(user));
            given(phoneOtps.findFirstByUserIdOrderByCreatedAtDesc(7L)).willReturn(Optional.of(otp));

            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(() -> service.verifyOtp(new VerifyOtpRequest(EMAIL, OTP_CODE)))
                    .satisfies(e -> assertThat(e.getCode()).isEqualTo("OTP_ATTEMPTS_EXCEEDED"));

            then(passwordEncoder).should(never()).matches(any(), any());
        }

        @Test
        void anExpiredCodeIsReportedAsExpiredRatherThanAsWrong() {
            Tutor user = tutor(7L);
            PhoneOtp otp = new PhoneOtp(user.getId(), "$2a$10$otp", NOW.minusSeconds(1));
            given(users.findByEmail(EMAIL)).willReturn(Optional.of(user));
            given(phoneOtps.findFirstByUserIdOrderByCreatedAtDesc(7L)).willReturn(Optional.of(otp));

            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(() -> service.verifyOtp(new VerifyOtpRequest(EMAIL, OTP_CODE)))
                    .satisfies(e -> assertThat(e.getCode()).isEqualTo("OTP_EXPIRED"));

            assertThat(otp.getAttemptCount()).isZero();
        }

        @Test
        void refusesWhenNoCodeWasEverSent() {
            Tutor user = tutor(7L);
            given(users.findByEmail(EMAIL)).willReturn(Optional.of(user));
            given(phoneOtps.findFirstByUserIdOrderByCreatedAtDesc(7L)).willReturn(Optional.empty());

            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(() -> service.verifyOtp(new VerifyOtpRequest(EMAIL, OTP_CODE)))
                    .satisfies(e -> assertThat(e.getCode()).isEqualTo("OTP_NOT_REQUESTED"));
        }

        @Test
        void sendingRefusesPastTheHourlyCap() {
            Tutor user = tutor(7L);
            given(users.findByEmail(EMAIL)).willReturn(Optional.of(user));
            given(phoneOtps.countByUserIdAndCreatedAtAfter(7L, NOW.minus(Duration.ofHours(1))))
                    .willReturn(3L);

            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(() -> service.sendOtp(new SendOtpRequest(EMAIL)))
                    .satisfies(e -> assertThat(e.getCode()).isEqualTo("OTP_SEND_LIMIT_EXCEEDED"));

            then(phoneOtps).should(never()).save(any(PhoneOtp.class));
            then(notifications).shouldHaveNoInteractions();
        }

        @Test
        void sendingStillWorksOneBelowTheCap() {
            Tutor user = tutor(7L);
            given(passwordEncoder.encode(anyString())).willAnswer(call -> hashOf(call.getArgument(0)));
            given(users.findByEmail(EMAIL)).willReturn(Optional.of(user));
            given(phoneOtps.countByUserIdAndCreatedAtAfter(7L, NOW.minus(Duration.ofHours(1))))
                    .willReturn(2L);

            service.sendOtp(new SendOtpRequest(EMAIL));

            then(phoneOtps).should().save(any(PhoneOtp.class));
            then(notifications).should().send(any(Notification.class));
        }

        @Test
        void sendingTextsThePhoneWhenDeliveryIsSms() {
            Tutor user = tutor(7L);
            given(passwordEncoder.encode(anyString())).willAnswer(call -> hashOf(call.getArgument(0)));
            given(users.findByEmail(EMAIL)).willReturn(Optional.of(user));

            service.sendOtp(new SendOtpRequest(EMAIL));

            then(notifications).should().send(sent.capture());
            assertThat(sent.getValue().getType()).isEqualTo(NotificationType.PHONE_OTP);
            assertThat(sent.getValue().getRecipient()).isEqualTo(PHONE);
        }

        /**
         * The interim setting while there is no SMS gateway account: same code, same hash,
         * same expiry, carried to the registered address instead of the handset.
         */
        @Test
        void sendingEmailsTheAddressWhenDeliveryIsEmail() {
            Tutor user = tutor(7L);
            given(passwordEncoder.encode(anyString())).willAnswer(call -> hashOf(call.getArgument(0)));
            given(users.findByEmail(EMAIL)).willReturn(Optional.of(user));
            AuthServiceImpl overEmail = serviceDelivering(OtpDelivery.EMAIL);

            overEmail.sendOtp(new SendOtpRequest(EMAIL));

            then(phoneOtps).should().save(any(PhoneOtp.class));
            then(notifications).should().send(sent.capture());
            assertThat(sent.getValue().getType()).isEqualTo(NotificationType.PHONE_OTP_EMAIL);
            assertThat(sent.getValue().getRecipient()).isEqualTo(EMAIL);
            assertThat(sent.getValue().getVariables()).containsKey("code");
        }

        @Test
        void sendingRevealsNothingAboutAnAddressWithNoAccount() {
            given(users.findByEmail("stranger@example.lk")).willReturn(Optional.empty());

            assertThatCode(() -> service.sendOtp(new SendOtpRequest("stranger@example.lk")))
                    .doesNotThrowAnyException();

            then(notifications).shouldHaveNoInteractions();
        }

        @Test
        void sendingDoesNothingWhenThePhoneIsAlreadyVerified() {
            Tutor user = tutor(7L);
            user.verifyPhone();
            given(users.findByEmail(EMAIL)).willReturn(Optional.of(user));

            service.sendOtp(new SendOtpRequest(EMAIL));

            then(phoneOtps).should(never()).save(any(PhoneOtp.class));
            then(notifications).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("phone verification switched off")
    class PhoneVerificationDisabled {

        private AuthServiceImpl disabled;

        @BeforeEach
        void setUp() {
            disabled = serviceWith(propertiesWithPhoneVerification(OtpDelivery.EMAIL, false));
        }

        @Test
        @DisplayName("a code request is refused rather than mailed to the same inbox")
        void refusesToSendACode() {
            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(() -> disabled.sendOtp(new SendOtpRequest(EMAIL)))
                    .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("PHONE_VERIFICATION_DISABLED"));

            // Refused before the address is even looked up: nothing is sent, nothing is
            // stored, and the caller learns nothing about who is registered.
            then(users).shouldHaveNoInteractions();
            then(phoneOtps).shouldHaveNoInteractions();
            then(notifications).shouldHaveNoInteractions();
        }

        @Test
        void refusesToVerifyACode() {
            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(() -> disabled.verifyOtp(new VerifyOtpRequest(EMAIL, "123456")))
                    .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("PHONE_VERIFICATION_DISABLED"));

            then(users).shouldHaveNoInteractions();
            then(phoneOtps).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("sign-in (FR-A4)")
    class SignIn {

        @Test
        void issuesAPairAndStampsTheLogin() {
            Tutor user = activeTutor(7L);
            given(users.findByEmail(EMAIL)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).willReturn(true);
            given(jwtService.issueAccessToken(user)).willReturn("access-token");
            given(jwtService.accessTokenTtl()).willReturn(Duration.ofMinutes(15));
            given(refreshTokenService.issueFor(7L)).willReturn("refresh-token");

            var tokens = service.login(new LoginRequest(EMAIL, PASSWORD));

            assertThat(tokens.accessToken()).isEqualTo("access-token");
            assertThat(tokens.refreshToken()).isEqualTo("refresh-token");
            assertThat(tokens.tokenType()).isEqualTo("Bearer");
            assertThat(tokens.expiresInSeconds()).isEqualTo(900);
            assertThat(user.getLastLoginAt()).isEqualTo(NOW);
        }

        @Test
        void answersAnUnknownAddressAndAWrongPasswordIdentically() {
            given(users.findByEmail(EMAIL)).willReturn(Optional.empty());

            AuthenticationFailedException unknownAddress = catchAuthFailure(EMAIL);

            Tutor user = activeTutor(7L);
            given(users.findByEmail(EMAIL)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).willReturn(false);

            AuthenticationFailedException wrongPassword = catchAuthFailure(EMAIL);

            assertThat(unknownAddress.getCode()).isEqualTo("INVALID_CREDENTIALS");
            assertThat(wrongPassword.getCode()).isEqualTo(unknownAddress.getCode());
            assertThat(wrongPassword.getMessage()).isEqualTo(unknownAddress.getMessage());
        }

        @Test
        void refusesAnAccountThatHasNotFinishedVerifying() {
            Tutor user = tutor(7L);
            given(users.findByEmail(EMAIL)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).willReturn(true);

            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(() -> service.login(new LoginRequest(EMAIL, PASSWORD)))
                    .satisfies(e -> assertThat(e.getCode()).isEqualTo("ACCOUNT_NOT_ACTIVE"));

            then(refreshTokenService).shouldHaveNoInteractions();
            assertThat(user.getLastLoginAt()).isNull();
        }

        private AuthenticationFailedException catchAuthFailure(String email) {
            try {
                service.login(new LoginRequest(email, PASSWORD));
                throw new AssertionError("expected sign-in to fail");
            } catch (AuthenticationFailedException e) {
                return e;
            }
        }
    }

    @Nested
    @DisplayName("refresh and logout (FR-A4)")
    class RefreshAndLogout {

        @Test
        void rotatesThePresentedTokenOut() {
            Tutor user = activeTutor(7L);
            RefreshToken session = new RefreshToken(7L, "digest", NOW.plus(Duration.ofDays(30)));
            given(refreshTokenService.requireActive("old-refresh")).willReturn(session);
            given(users.findById(7L)).willReturn(Optional.of(user));
            given(jwtService.issueAccessToken(user)).willReturn("new-access");
            given(jwtService.accessTokenTtl()).willReturn(Duration.ofMinutes(15));
            given(refreshTokenService.issueFor(7L)).willReturn("new-refresh");

            var tokens = service.refresh(new RefreshTokenRequest("old-refresh"));

            then(refreshTokenService).should().revoke(session);
            assertThat(tokens.refreshToken()).isEqualTo("new-refresh");
        }

        @Test
        void endsEverySessionWhenTheAccountCanNoLongerSignIn() {
            Tutor user = activeTutor(7L);
            user.suspend();
            RefreshToken session = new RefreshToken(7L, "digest", NOW.plus(Duration.ofDays(30)));
            given(refreshTokenService.requireActive("old-refresh")).willReturn(session);
            given(users.findById(7L)).willReturn(Optional.of(user));

            assertThatExceptionOfType(AuthenticationFailedException.class)
                    .isThrownBy(() -> service.refresh(new RefreshTokenRequest("old-refresh")))
                    .satisfies(e -> assertThat(e.getCode()).isEqualTo("ACCOUNT_NOT_ACTIVE"));

            then(refreshTokenService).should().revokeAllFor(7L);
        }

        @Test
        void loggingOutEndsTheSessionTheTokenIdentifies() {
            service.logout(new RefreshTokenRequest("some-refresh"));

            then(refreshTokenService).should().revokeIfPresent("some-refresh");
        }
    }

    @Nested
    @DisplayName("password recovery (FR-A6)")
    class PasswordRecovery {

        @Test
        void emailsALinkForAnAddressThatHasAnAccount() {
            Tutor user = activeTutor(7L);
            given(users.findByEmail(EMAIL)).willReturn(Optional.of(user));

            service.forgotPassword(new ForgotPasswordRequest(EMAIL));

            then(passwordResetTokens).should().save(any(PasswordResetToken.class));
            then(notifications).should().send(sent.capture());
            assertThat(sent.getValue().getType()).isEqualTo(NotificationType.PASSWORD_RESET);
            assertThat((String) sent.getValue().getVariables().get("resetUrl"))
                    .startsWith("https://tutorspoint.test/reset-password?token=");
            assertThat(sent.getValue().getVariables()).containsEntry("expiryMinutes", 30L);
        }

        @Test
        void revealsNothingAboutAnAddressWithNoAccount() {
            given(users.findByEmail("stranger@example.lk")).willReturn(Optional.empty());

            assertThatCode(() -> service.forgotPassword(new ForgotPasswordRequest("stranger@example.lk")))
                    .doesNotThrowAnyException();

            then(passwordResetTokens).shouldHaveNoInteractions();
            then(notifications).shouldHaveNoInteractions();
        }

        @Test
        void resettingChangesTheHashAndSignsTheAccountOutEverywhere() {
            Tutor user = activeTutor(7L);
            PasswordResetToken token = new PasswordResetToken(
                    7L, SecureTokens.sha256Hex(RAW_LINK_TOKEN), NOW.plus(Duration.ofMinutes(30)));
            given(passwordResetTokens.findByTokenHash(SecureTokens.sha256Hex(RAW_LINK_TOKEN)))
                    .willReturn(Optional.of(token));
            given(users.findById(7L)).willReturn(Optional.of(user));
            given(passwordEncoder.encode("Kandy2026new")).willReturn("$2a$10$newhash");

            service.resetPassword(new ResetPasswordRequest(RAW_LINK_TOKEN, "Kandy2026new"));

            assertThat(user.getPasswordHash()).isEqualTo("$2a$10$newhash");
            assertThat(token.isConsumed()).isTrue();
            then(refreshTokenService).should().revokeAllFor(7L);
        }

        @Test
        void refusesASpentResetToken() {
            PasswordResetToken token = new PasswordResetToken(
                    7L, SecureTokens.sha256Hex(RAW_LINK_TOKEN), NOW.plus(Duration.ofMinutes(30)));
            token.consume(NOW.minusSeconds(10));
            given(passwordResetTokens.findByTokenHash(SecureTokens.sha256Hex(RAW_LINK_TOKEN)))
                    .willReturn(Optional.of(token));

            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(() -> service.resetPassword(
                            new ResetPasswordRequest(RAW_LINK_TOKEN, "Kandy2026new")))
                    .satisfies(e -> assertThat(e.getCode())
                            .isEqualTo("PASSWORD_RESET_TOKEN_ALREADY_USED"));

            then(refreshTokenService).shouldHaveNoInteractions();
        }

        @Test
        void refusesAnUnknownResetToken() {
            given(passwordResetTokens.findByTokenHash(SecureTokens.sha256Hex(RAW_LINK_TOKEN)))
                    .willReturn(Optional.empty());

            assertThatExceptionOfType(BusinessRuleViolationException.class)
                    .isThrownBy(() -> service.resetPassword(
                            new ResetPasswordRequest(RAW_LINK_TOKEN, "Kandy2026new")))
                    .satisfies(e -> assertThat(e.getCode())
                            .isEqualTo("PASSWORD_RESET_TOKEN_INVALID"));
        }
    }

    private static String hashOf(String raw) {
        return "$2a$10$" + raw;
    }

    private static RegisterRequest registerAs(RegistrableRole role) {
        return new RegisterRequest(role, EMAIL, PASSWORD, "Nimali Perera", PHONE, Language.SI);
    }

    private static Tutor tutor(Long id) {
        Tutor tutor = new Tutor(EMAIL, PASSWORD_HASH, "Nimali Perera", PHONE, Language.SI);
        return withId(tutor, id);
    }

    private static Tutor activeTutor(Long id) {
        Tutor tutor = tutor(id);
        tutor.verifyEmail();
        tutor.verifyPhone();
        tutor.activate();
        return tutor;
    }

    private static EmailVerificationToken emailToken(Long userId, Instant expiresAt) {
        return new EmailVerificationToken(userId, SecureTokens.sha256Hex(RAW_LINK_TOKEN), expiresAt);
    }

    private static PhoneOtp liveOtp(Long userId) {
        return new PhoneOtp(userId, "$2a$10$otpdigest", NOW.plus(Duration.ofMinutes(5)));
    }

    /**
     * Ids are assigned by the database, and there is no setter for one by design. The unit
     * tests still need an account that knows its own id, so it is set the only way left.
     */
    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
