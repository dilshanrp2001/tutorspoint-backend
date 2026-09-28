package com.tutorspoint.notification.sms;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Notify.lk gateway credentials, supplied by the environment. Validated so a
 * deployment missing one fails at startup rather than on the first OTP.
 */
@Validated
@ConfigurationProperties(prefix = "tutorspoint.notification.sms.notify-lk")
public record NotifyLkProperties(

        /** Gateway API root, for example https://app.notify.lk/api/v1. */
        @NotBlank String baseUrl,

        @NotBlank String userId,

        @NotBlank String apiKey,

        /** The registered alphanumeric sender mask shown on the handset. */
        @NotBlank String senderId) {
}
