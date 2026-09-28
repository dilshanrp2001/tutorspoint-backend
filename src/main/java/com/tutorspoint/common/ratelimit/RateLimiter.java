package com.tutorspoint.common.ratelimit;

import java.time.Duration;
import java.util.Optional;

/**
 * A token bucket per key, in our own vocabulary. The library behind it lives in exactly one
 * adapter ({@link Bucket4jRateLimiter}); replacing it - with a Redis-backed store once there is
 * more than one instance, say - is a new class, not an edit to the filter.
 */
public interface RateLimiter {

    /**
     * Takes one request's worth from the bucket that {@code key} names, creating the bucket
     * full on first use.
     *
     * @return empty when the request may proceed; otherwise how long until it would be allowed
     */
    Optional<Duration> tryAcquire(String key, RateLimitProperties.Limit limit);
}
