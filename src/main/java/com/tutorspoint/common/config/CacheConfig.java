package com.tutorspoint.common.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Turns on {@code @Cacheable}, backed by Spring Boot's auto-configured in-memory
 * {@code ConcurrentMapCacheManager}.
 *
 * <p>In-memory and unbounded is the right size for what is cached today: reference data —
 * a few hundred rows that change only when a migration runs, keyed by the three languages,
 * so the cache has a fixed, tiny ceiling and can never go stale within a deployment.
 *
 * <p>Nothing request-scoped, user-scoped or large belongs in here. The moment something
 * does, this becomes a real cache provider with eviction and a TTL (architecture §14: no
 * caching layer before a requirement demands one) — and that is a change to this file
 * rather than to every annotated method.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
