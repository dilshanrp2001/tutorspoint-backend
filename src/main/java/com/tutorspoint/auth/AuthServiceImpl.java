package com.tutorspoint.auth;

import com.tutorspoint.auth.config.AuthProperties;
import com.tutorspoint.auth.config.OtpDelivery;
import com.tutorspoint.auth.domain.AccountStatus;
import com.tutorspoint.auth.domain.EmailVerificationToken;
import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.Student;
import com.tutorspoint.auth.domain.PasswordResetToken;
import com.tutorspoint.auth.domain.PhoneOtp;
import com.tutorspoint.auth.domain.RefreshToken;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.domain.User;
import com.tutorspoint.auth.dto.AccountResponse;
import com.tutorspoint.auth.dto.AuthTokensResponse;
import com.tutorspoint.auth.dto.ForgotPasswordRequest;
import com.tutorspoint.auth.dto.LoginRequest;
import com.tutorspoint.auth.dto.RefreshTokenRequest;
import com.tutorspoint.auth.dto.RegisterRequest;
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
import com.tutorspoint.common.exception.AuthenticationFailedException;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.common.exception.ResourceNotFoundException;
import com.tutorspoint.notification.NotificationService;
import com.tutorspoint.notification.domain.Notification;
import com.tutorspoint.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * The account state machine and the flows that drive it.
 *
 * <p>Three habits run through this class:
 *
 * <ul>
 *   <li><strong>The entity decides what a transition means.</strong> Expiry, single use,
 *       attempt ceilings, "both channels verified before ACTIVE", "only ACTIVE may sign in"
 *       — all of that lives on {@code User} and {@code SingleUseToken}. This service moves
 *       secrets and messages around and asks the entity to change state.</li>
 *   <li><strong>Nothing leaks who has an account.</strong> Forgot-password and send-OTP
 *       return the same way for a stranger as for a member, and a failed sign-in cannot tell
 *       a wrong password from an unknown address.</li>
 *   <li><strong>Sending is never in the critical path.</strong> Notifications are
 *       fire-and-forget, so a dead SMS gateway cannot roll back a registration.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** Six digits, per FR-A2. The attempt ceiling lives on {@link PhoneOtp}. */
    private static final int OTP_DIGITS = 6;

    private static final String ERROR_EMAIL_TAKEN = "EMAIL_ALREADY_REGISTERED";
    private static final String ERROR_PHONE_TAKEN = "PHONE_ALREADY_REGISTERED";
    private static final String ERROR_OTP_SEND_LIMIT = "OTP_SEND_LIMIT_EXCEEDED";
    private static final String ERROR_OTP_INVALID = "OTP_INVALID";
    private static final String ERROR_OTP_NOT_REQUESTED = "OTP_NOT_REQUESTED";
    private static final String ERROR_EMAIL_TOKEN_INVALID = "EMAIL_VERIFICATION_TOKEN_INVALID";
    private static final String ERROR_RESET_TOKEN_INVALID = "PASSWORD_RESET_TOKEN_INVALID";
    private static final String ERROR_INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    private static final String ERROR_ACCOUNT_NOT_ACTIVE = "ACCOUNT_NOT_ACTIVE";

    private final UserRepository users;
    private final EmailVerificationTokenRepository emailTokens;
    private final PhoneOtpRepository phoneOtps;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final NotificationService notifications;
    private final PasswordEncoder passwordEncoder;
    private final AccountMapper accountMapper;
    private final AuthProperties authProperties;
    private final Clock clock;

    @Override
    @Transactional
    public AccountResponse register(RegisterRequest request) {
        String email = normalise(request.email());
        String phoneNumber = request.phoneNumber().trim();

        // The unique constraints are the real guarantee against a race; these two checks
        // exist to answer with a usable error instead of a 500 from the database.
        if (users.existsByEmail(email)) {
            throw new BusinessRuleViolationException(ERROR_EMAIL_TAKEN,
                    "An account already exists for this email address");
        }
        if (users.existsByPhoneNumber(phoneNumber)) {
            throw new BusinessRuleViolationException(ERROR_PHONE_TAKEN,
                    "An account already exists for this phone number");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = switch (request.role()) {
            case TUTOR -> new Tutor(email, passwordHash, request.fullName(), phoneNumber,
                    request.preferredLanguage());
            case PARENT -> new Parent(email, passwordHash, request.fullName(), phoneNumber,
                    request.preferredLanguage());
            case STUDENT -> new Student(email, passwordHash, request.fullName(), phoneNumber,
                    request.preferredLanguage());
        };
        user = users.saveAndFlush(user);
        log.info("Registered account {} as {}", user.getId(), user.getRole());

        sendEmailVerification(user);
        sendOtpTo(user);
        return accountMapper.toAccountResponse(user);
    }

    @Override
    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        EmailVerificationToken token = emailTokens
                .findByTokenHash(SecureTokens.sha256Hex(request.token()))
                .orElseThrow(() -> new BusinessRuleViolationException(ERROR_EMAIL_TOKEN_INVALID,
                        "This verification link is not valid"));

        token.consume(clock.instant());
        User user = requireUser(token.getUserId());
        user.verifyEmail();
        activateIfFullyVerified(user);
        log.info("Email verified for account {}", user.getId());
    }

    /**
     * Unknown address and already-verified phone both return quietly. Only the throttle can
     * fail loudly, because the client has to be able to tell the user to wait — and reaching
     * it means the caller has already had three codes sent to that account, so it tells them
     * nothing they did not already know.
     */
    @Override
    @Transactional
    public void sendOtp(SendOtpRequest request) {
        Optional<User> found = users.findByEmail(normalise(request.email()));
        if (found.isEmpty()) {
            log.debug("OTP requested for an address with no account");
            return;
        }
        User user = found.get();
        if (user.isPhoneVerified() || user.getStatus() == AccountStatus.DELETED) {
            log.debug("OTP not sent for account {}: nothing to verify", user.getId());
            return;
        }
        sendOtpTo(user);
    }

    @Override
    @Transactional
    public void verifyOtp(VerifyOtpRequest request) {
        User user = users.findByEmail(normalise(request.email()))
                .orElseThrow(() -> new BusinessRuleViolationException(ERROR_OTP_INVALID,
                        "That code is not valid"));

        PhoneOtp otp = phoneOtps.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new BusinessRuleViolationException(ERROR_OTP_NOT_REQUESTED,
                        "No code has been sent to this account yet"));

        Instant now = clock.instant();
        // Ask first, so an expired or burnt code is reported as such rather than as a wrong
        // guess — and so a wrong guess against a dead code does not consume an attempt.
        otp.ensureRedeemable(now);

        if (!passwordEncoder.matches(request.code(), otp.getCodeHash())) {
            otp.recordFailedAttempt();
            phoneOtps.save(otp);
            log.info("Wrong OTP for account {} (attempt {})", user.getId(), otp.getAttemptCount());
            throw new BusinessRuleViolationException(ERROR_OTP_INVALID, "That code is not valid");
        }

        otp.consume(now);
        user.verifyPhone();
        activateIfFullyVerified(user);
        log.info("Phone verified for account {}", user.getId());
    }

    @Override
    @Transactional
    public AuthTokensResponse login(LoginRequest request) {
        User user = users.findByEmail(normalise(request.email()))
                .orElseThrow(() -> invalidCredentials(request.email()));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials(request.email());
        }
        if (user.getStatus() != AccountStatus.ACTIVE) {
            // A distinct code, not a vaguer one: the caller has already proved they own
            // this account, and the frontend has to route them to verification.
            throw new BusinessRuleViolationException(ERROR_ACCOUNT_NOT_ACTIVE,
                    "This account is %s and cannot sign in".formatted(user.getStatus()));
        }

        user.recordLogin(clock.instant());
        log.info("Account {} signed in", user.getId());
        return issueTokens(user);
    }

    @Override
    @Transactional
    public AuthTokensResponse refresh(RefreshTokenRequest request) {
        RefreshToken session = refreshTokenService.requireActive(request.refreshToken());
        User user = requireUser(session.getUserId());
        if (user.getStatus() != AccountStatus.ACTIVE) {
            refreshTokenService.revokeAllFor(user.getId());
            throw new AuthenticationFailedException(ERROR_ACCOUNT_NOT_ACTIVE,
                    "This account can no longer sign in");
        }

        // Rotation: the presented token dies here, so a leaked one is good for one use at
        // most and its reuse is detectable.
        refreshTokenService.revoke(session);
        return issueTokens(user);
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revokeIfPresent(request.refreshToken());
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        Optional<User> found = users.findByEmail(normalise(request.email()));
        if (found.isEmpty() || found.get().getStatus() == AccountStatus.DELETED) {
            // Deliberately indistinguishable from success, including in timing terms at the
            // only resolution that matters here: no mail is sent and nothing is said.
            log.debug("Password reset requested for an address with no usable account");
            return;
        }

        User user = found.get();
        String token = SecureTokens.newLinkToken();
        Duration ttl = authProperties.passwordResetTtl();
        passwordResetTokens.save(new PasswordResetToken(
                user.getId(), SecureTokens.sha256Hex(token), clock.instant().plus(ttl)));

        notifications.send(Notification.builder()
                .type(NotificationType.PASSWORD_RESET)
                .recipient(user.getEmail())
                .language(user.getPreferredLanguage())
                .variable("fullName", user.getFullName())
                .variable("resetUrl", linkWithToken(authProperties.resetPasswordUrl(), token))
                .variable("expiryMinutes", ttl.toMinutes())
                .build());
        log.info("Password reset link sent for account {}", user.getId());
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = passwordResetTokens
                .findByTokenHash(SecureTokens.sha256Hex(request.token()))
                .orElseThrow(() -> new BusinessRuleViolationException(ERROR_RESET_TOKEN_INVALID,
                        "This reset link is not valid"));

        token.consume(clock.instant());
        User user = requireUser(token.getUserId());
        user.changePassword(passwordEncoder.encode(request.newPassword()));

        // Whoever held the old password is signed out. A reset is how somebody recovers an
        // account they had lost control of, so leaving existing sessions alive would leave
        // the intruder signed in.
        refreshTokenService.revokeAllFor(user.getId());
        log.info("Password reset for account {}", user.getId());
    }

    /** Emails a fresh verification link (FR-A3). */
    private void sendEmailVerification(User user) {
        String token = SecureTokens.newLinkToken();
        Duration ttl = authProperties.emailVerificationTtl();
        emailTokens.save(new EmailVerificationToken(
                user.getId(), SecureTokens.sha256Hex(token), clock.instant().plus(ttl)));

        notifications.send(Notification.builder()
                .type(NotificationType.EMAIL_VERIFICATION)
                .recipient(user.getEmail())
                .language(user.getPreferredLanguage())
                .variable("fullName", user.getFullName())
                .variable("verificationUrl", linkWithToken(authProperties.verifyEmailUrl(), token))
                .variable("expiryHours", ttl.toHours())
                .build());
    }

    /**
     * Sends a fresh code (FR-A2), refusing past the hourly cap. The cap outlives the
     * transport: an SMS costs money per message, and an emailed code is still a live
     * secret that nobody should be able to spray at an account.
     *
     * <p>Which transport carries it is configuration, not a decision made here — see
     * {@link OtpDelivery}. Everything else about the code is identical either way: the
     * same six digits, the same hash, the same expiry, checked by the same
     * {@link #verifyOtp}.
     */
    private void sendOtpTo(User user) {
        Instant now = clock.instant();
        long sentThisHour = phoneOtps.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(Duration.ofHours(1)));
        if (sentThisHour >= authProperties.maxOtpSendsPerHour()) {
            throw new BusinessRuleViolationException(ERROR_OTP_SEND_LIMIT,
                    "Too many codes have been sent to this account in the last hour");
        }

        String code = SecureTokens.newNumericCode(OTP_DIGITS);
        Duration ttl = authProperties.otpTtl();
        phoneOtps.save(new PhoneOtp(user.getId(), passwordEncoder.encode(code), now.plus(ttl)));

        boolean overEmail = authProperties.otpDelivery() == OtpDelivery.EMAIL;
        notifications.send(Notification.builder()
                .type(overEmail ? NotificationType.PHONE_OTP_EMAIL : NotificationType.PHONE_OTP)
                .recipient(overEmail ? user.getEmail() : user.getPhoneNumber())
                .language(user.getPreferredLanguage())
                .variable("code", code)
                .variable("expiryMinutes", ttl.toMinutes())
                // Only the email template greets the reader; an unused variable costs a
                // template nothing, and keeps this one send call.
                .variable("fullName", user.getFullName())
                .build());
    }

    /**
     * Opens the account as soon as both channels are confirmed, in whichever order they
     * arrive. A suspended account is not reopened by re-verifying: that is a moderation
     * decision, and only an administrator reverses it.
     */
    private void activateIfFullyVerified(User user) {
        if (user.getStatus() == AccountStatus.PENDING_VERIFICATION && user.isFullyVerified()) {
            user.activate();
            log.info("Account {} activated", user.getId());
        }
    }

    private AuthTokensResponse issueTokens(User user) {
        return AuthTokensResponse.bearer(
                jwtService.issueAccessToken(user),
                refreshTokenService.issueFor(user.getId()),
                jwtService.accessTokenTtl().toSeconds());
    }

    /**
     * A token row without its account is a broken foreign key, not a user error — the
     * cascade deletes tokens with the account, so this cannot happen by any normal path.
     */
    private User requireUser(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", userId));
    }

    /** One exception for both sign-in failures, so neither can be told from the other. */
    private AuthenticationFailedException invalidCredentials(String attemptedEmail) {
        log.info("Failed sign-in attempt for {}", maskEmail(attemptedEmail));
        return new AuthenticationFailedException(ERROR_INVALID_CREDENTIALS,
                "Email address or password is incorrect");
    }

    private static String linkWithToken(String baseUrl, String token) {
        return baseUrl + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    /** The entity lower-cases on the way in; lookups must search the same way. */
    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Failed sign-ins are worth logging; the application log is not a contact list. */
    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.charAt(0) + "***" + email.substring(at) : "***";
    }
}
