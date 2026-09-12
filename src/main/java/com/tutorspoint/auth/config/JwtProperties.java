package com.tutorspoint.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * How access tokens are signed and how long the two token kinds live.
 *
 * <p>The secret is read from the environment and never has a default — a build that can
 * start without one would eventually start in production with a known key. Validation
 * here means a missing or short secret fails startup rather than surfacing as forged
 * tokens later.
 */
@Validated
@ConfigurationProperties(prefix = "tutorspoint.security.jwt")
public record JwtProperties(

        /** Base64-encoded signing key, at least 256 bits. {@code openssl rand -base64 32}. */
        @NotBlank String secret,

        /** The {@code iss} claim, so a token minted for another system is rejected. */
        @NotBlank String issuer,

        /** Short, because nothing can revoke an access token before it expires. */
        @NotNull Duration accessTokenTtl,

        /** Long: the refresh token is what keeps a user signed in, and it is revocable. */
        @NotNull Duration refreshTokenTtl) {
}
