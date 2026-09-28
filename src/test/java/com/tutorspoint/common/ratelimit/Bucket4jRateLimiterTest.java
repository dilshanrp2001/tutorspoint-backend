package com.tutorspoint.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class Bucket4jRateLimiterTest {

    private static final RateLimitProperties.Limit THREE_PER_HOUR = new RateLimitProperties.Limit(3, Duration.ofHours(1));

    private final RateLimiter limiter = new Bucket4jRateLimiter(Duration.ofHours(1));

    @Test
    void allowsTheConfiguredNumberThenReportsHowLongToWait() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("k", THREE_PER_HOUR)).as("request %d", i + 1).isEmpty();
        }

        Optional<Duration> wait = limiter.tryAcquire("k", THREE_PER_HOUR);

        // Refilled smoothly: one request's worth comes back after a third of the hour.
        assertThat(wait).hasValueSatisfying(duration -> assertThat(duration)
                .isPositive()
                .isLessThanOrEqualTo(Duration.ofMinutes(20)));
    }

    @Test
    void eachKeyHasItsOwnBucket() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("first", THREE_PER_HOUR);
        }

        assertThat(limiter.tryAcquire("first", THREE_PER_HOUR)).isPresent();
        assertThat(limiter.tryAcquire("second", THREE_PER_HOUR)).isEmpty();
    }
}
