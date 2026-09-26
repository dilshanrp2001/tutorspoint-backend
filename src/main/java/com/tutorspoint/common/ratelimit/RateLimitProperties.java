package com.tutorspoint.common.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The HTTP rate limits. Policy rather than secrets, so the numbers live in
 * {@code application.yml}.
 *
 * <p>Every {@link RateLimitedAction} must be configured: an action missing from the file would
 * otherwise be silently unlimited, which is exactly the mistake this class exists to catch at
 * startup rather than in production.
 */
@Validated
@ConfigurationProperties(prefix = "tutorspoint.rate-limit")
public record RateLimitProperties(

        /** Off only in the integration-test profile, where every request comes from one address. */
        boolean enabled,

        @NotNull @Valid Map<RateLimitedAction, ActionLimits> actions) {

    public RateLimitProperties {
        if (actions != null) {
            Set<RateLimitedAction> missing = EnumSet.allOf(RateLimitedAction.class);
            missing.removeAll(actions.keySet());
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException(
                        "tutorspoint.rate-limit.actions has no limits for " + missing);
            }
            actions = Map.copyOf(actions);
        }
    }

    public ActionLimits limitsFor(RateLimitedAction action) {
        return actions.get(action);
    }

    /** The longest window configured anywhere; an idle bucket is kept at least this long. */
    public Duration longestWindow() {
        return actions.values().stream()
                .flatMap(limits -> Stream.of(limits.perClient(), limits.perAccount()))
                .filter(Objects::nonNull)
                .map(Limit::per)
                .max(Duration::compareTo)
                .orElse(Duration.ofHours(1));
    }

    /**
     * @param perClient  per client address. Required: it is the limit that applies to everyone.
     * @param perAccount per account - the signed-in user, or the email address an anonymous
     *                   request names. Optional, for an action with no account to key on.
     */
    public record ActionLimits(@NotNull @Valid Limit perClient, @Valid Limit perAccount) {
    }

    /** {@code requests} per {@code per}, refilled smoothly across the window. */
    public record Limit(@Min(1) int requests, @NotNull Duration per) {
    }
}
