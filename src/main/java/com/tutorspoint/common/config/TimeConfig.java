package com.tutorspoint.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * The clock the application reads "now" from.
 *
 * <p>Injected rather than called statically, so expiry and throttling windows are
 * exercised by unit tests with a fixed clock instead of by sleeping. UTC: every instant
 * the application stores or compares is absolute, never the server's local time.
 */
@Configuration
public class TimeConfig {

    /**
     * The zone a calendar day means on this platform. Every instant is stored in UTC; this is
     * used only where a person asks about a date - "searches on 16 September" - and the answer
     * has to be the day a Sri Lankan user lived through, not the UTC one.
     */
    public static final ZoneId PLATFORM_ZONE = ZoneId.of("Asia/Colombo");

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
