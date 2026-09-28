package com.tutorspoint.common.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.List;

/**
 * The browser origins allowed to call the API cross-origin: the frontend, and nothing else.
 *
 * <p>Checked at startup rather than trusted. A wildcard, or a value that is not a bare origin,
 * fails the boot - a CORS policy that quietly allows everyone is indistinguishable from a
 * working one until somebody else's page starts calling the API with a user's token.
 */
@Validated
@ConfigurationProperties(prefix = "tutorspoint.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins != null) {
            allowedOrigins.forEach(CorsProperties::requireBareOrigin);
            allowedOrigins = List.copyOf(allowedOrigins);
        }
    }

    private static void requireBareOrigin(String origin) {
        if (origin == null || origin.contains("*")) {
            throw new IllegalArgumentException("tutorspoint.cors.allowed-origins must name origins, not wildcards: " + origin);
        }
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("tutorspoint.cors.allowed-origins has an invalid origin: " + origin, e);
        }
        boolean httpScheme = "https".equals(uri.getScheme()) || "http".equals(uri.getScheme());
        boolean bare = uri.getHost() != null
                && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                && uri.getRawQuery() == null && uri.getRawFragment() == null && uri.getRawUserInfo() == null;
        if (!httpScheme || !bare) {
            throw new IllegalArgumentException(
                    "tutorspoint.cors.allowed-origins must be scheme://host[:port] with no path: " + origin);
        }
    }
}
