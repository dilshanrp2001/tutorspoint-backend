package com.tutorspoint.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import java.time.Duration;
import java.util.Optional;

/**
 * {@link RateLimiter} on Bucket4j, in memory. The only class that knows Bucket4j exists.
 *
 * <p>In memory is right for the pilot's single instance; with a second instance each would
 * enforce its own copy of every limit, and this becomes a distributed store instead.
 *
 * <p>The buckets sit in a bounded Caffeine cache, not a map. A map keyed by client address
 * grows with every address that ever called, which lets a spoofed-source flood exhaust the
 * heap through the very component meant to protect it. An idle bucket is dropped once it would
 * have refilled anyway, so eviction never hands a client a fresher bucket than waiting would.
 */
public class Bucket4jRateLimiter implements RateLimiter {

    private static final long MAX_TRACKED_KEYS = 100_000;

    private final Cache<String, Bucket> buckets;

    public Bucket4jRateLimiter(Duration longestWindow) {
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_KEYS)
                .expireAfterAccess(longestWindow)
                .build();
    }

    @Override
    public Optional<Duration> tryAcquire(String key, RateLimitProperties.Limit limit) {
        Bucket bucket = buckets.get(key, ignored -> newBucket(limit));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        return probe.isConsumed()
                ? Optional.empty()
                : Optional.of(Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    private static Bucket newBucket(RateLimitProperties.Limit limit) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit.requests())
                        .refillGreedy(limit.requests(), limit.per())
                        .build())
                .build();
    }
}
