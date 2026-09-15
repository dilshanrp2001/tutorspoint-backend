package com.tutorspoint.tutor.seed;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fills a local database with demo tutors on startup, so search has something to search
 * (Phase 3). Dev profile only, and idempotent: once the demo tutors exist it does nothing, so a
 * restart never duplicates them or undoes a change made to one.
 *
 * <p>How many comes from {@code tutorspoint.seed.demo-tutors}; zero turns it off.
 */
@Slf4j
@Component
@Profile("dev")
public class DemoTutorSeedRunner implements CommandLineRunner {

    /** Fixed, so every developer's local directory holds the same tutors. */
    private static final long RANDOM_SEED = 20_260_915L;

    private final DemoTutorSeeder seeder;
    private final int demoTutors;

    public DemoTutorSeedRunner(DemoTutorSeeder seeder, @Value("${tutorspoint.seed.demo-tutors:0}") int demoTutors) {
        this.seeder = seeder;
        this.demoTutors = demoTutors;
    }

    @Override
    public void run(String... args) {
        if (demoTutors <= 0) {
            return;
        }
        if (seeder.alreadySeeded()) {
            log.debug("Demo tutors already present; not seeding");
            return;
        }
        log.info("Seeding {} demo tutors (sign in as {} / {})",
                demoTutors, DemoTutorSeeder.emailFor(1), DemoTutorSeeder.DEMO_PASSWORD);
        seeder.seed(demoTutors, RANDOM_SEED);
    }
}
