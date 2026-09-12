package com.tutorspoint.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The clock the application reads "now" from.
 *
 * <p>Injected rather than called statically, so expiry and throttling windows are
 * exercised by unit tests with a fixed clock instead of by sleeping. UTC: every instant
 * the application stores or compares is absolute, never the server's local time.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
