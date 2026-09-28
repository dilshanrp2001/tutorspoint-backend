package com.tutorspoint.notification.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Who platform email appears to come from. Bound from the environment, never
 * hardcoded, and validated so a missing value fails startup instead of surfacing as
 * rejected mail later.
 */
@Validated
@ConfigurationProperties(prefix = "tutorspoint.notification.email")
public record NotificationEmailProperties(

        /** Envelope and header From address, for example no-reply@tutorspoint.xyz. */
        @NotBlank @Email String from,

        /** Display name shown beside the address in the recipient's client. */
        @NotBlank String fromName) {
}
