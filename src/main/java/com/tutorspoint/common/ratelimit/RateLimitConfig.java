package com.tutorspoint.common.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Binds and validates the rate limits at startup, and chooses the limiter behind them. */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    public RateLimiter rateLimiter(RateLimitProperties properties) {
        return new Bucket4jRateLimiter(properties.longestWindow());
    }
}
