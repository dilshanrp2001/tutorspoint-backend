package com.tutorspoint.common.config;

import com.tutorspoint.common.ratelimit.RateLimitProperties;
import com.tutorspoint.common.ratelimit.RateLimitProperties.ActionLimits;
import com.tutorspoint.common.ratelimit.RateLimitProperties.Limit;
import com.tutorspoint.common.ratelimit.RateLimitedAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The two security settings that must fail startup rather than run permissively: a CORS origin
 * that is not exactly one origin, and a rate-limited action with no limit configured.
 */
class SecurityPropertiesTest {

    @Test
    void acceptsBareOrigins() {
        CorsProperties cors = new CorsProperties(List.of("https://tutorspoint.xyz", "http://localhost:5173"));

        assertThat(cors.allowedOrigins()).containsExactly("https://tutorspoint.xyz", "http://localhost:5173");
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "https://*.tutorspoint.xyz", "https://tutorspoint.xyz/app", "tutorspoint.xyz",
            "ftp://tutorspoint.xyz", "https://user@tutorspoint.xyz", "https://tutorspoint.xyz?x=1"})
    void refusesAnythingButABareOrigin(String origin) {
        assertThatIllegalArgumentException().isThrownBy(() -> new CorsProperties(List.of(origin)));
    }

    @Test
    void refusesARateLimitConfigurationThatLeavesAnActionUnlimited() {
        Map<RateLimitedAction, ActionLimits> actions = new EnumMap<>(RateLimitedAction.class);
        for (RateLimitedAction action : RateLimitedAction.values()) {
            actions.put(action, new ActionLimits(new Limit(10, Duration.ofMinutes(1)), null));
        }
        actions.remove(RateLimitedAction.SEARCH);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RateLimitProperties(true, actions))
                .withMessageContaining("SEARCH");
    }

    @Test
    void keepsIdleBucketsForTheLongestConfiguredWindow() {
        Map<RateLimitedAction, ActionLimits> actions = new EnumMap<>(RateLimitedAction.class);
        for (RateLimitedAction action : RateLimitedAction.values()) {
            actions.put(action, new ActionLimits(new Limit(10, Duration.ofMinutes(1)), null));
        }
        actions.put(RateLimitedAction.REGISTER,
                new ActionLimits(new Limit(10, Duration.ofMinutes(5)), new Limit(3, Duration.ofHours(2))));

        assertThat(new RateLimitProperties(true, actions).longestWindow()).isEqualTo(Duration.ofHours(2));
    }
}
