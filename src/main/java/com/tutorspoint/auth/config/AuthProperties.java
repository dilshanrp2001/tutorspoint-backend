package com.tutorspoint.auth.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * The verification and recovery policy: how long each secret is good for, how often one
 * may be sent, and where the links in those messages point.
 *
 * <p>The lifetimes are policy rather than secrets, so they carry defaults in
 * {@code application.yml}. The link targets are frontend URLs and therefore differ per
 * environment, which is why they come from {@code FRONTEND_BASE_URL}.
 */
@Validated
@ConfigurationProperties(prefix = "tutorspoint.auth")
public record AuthProperties(

        /** Generous: people open email hours later (FR-A3). */
        @NotNull Duration emailVerificationTtl,

        /** Short: a reset link in a forwarded mailbox is a live account (FR-A6). */
        @NotNull Duration passwordResetTtl,

        /** Five minutes, per the OTP rules (FR-A2). */
        @NotNull Duration otpTtl,

        /**
         * Whether phone verification runs at all (FR-A2). False until an SMS gateway
         * account exists: registration then sends only the email verification link, and
         * the two OTP endpoints refuse rather than mail a second code to the same inbox.
         *
         * <p>Turning this on is the whole switch — set it with {@code otpDelivery=SMS},
         * and restore {@code && phoneVerified} in {@code User#isFullyVerified()} so an
         * account again needs both channels.
         */
        boolean phoneVerificationEnabled,

        /**
         * Which transport carries the code. {@link OtpDelivery#SMS} is the requirement;
         * {@link OtpDelivery#EMAIL} is the free interim setting, and is the default until
         * a gateway account exists.
         */
        @NotNull OtpDelivery otpDelivery,

        /**
         * Sends allowed per account per hour. Every SMS costs money, so this caps both
         * abuse and an impatient user's resend button.
         */
        @Min(1) int maxOtpSendsPerHour,

        /** Frontend page that posts the emailed token to {@code /api/auth/verify-email}. */
        @NotBlank String verifyEmailUrl,

        /** Frontend page that collects the new password and posts the reset token. */
        @NotBlank String resetPasswordUrl) {
}
