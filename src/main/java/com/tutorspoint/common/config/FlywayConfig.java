package com.tutorspoint.common.config;

import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Makes a schema this build does not recognise stop startup, instead of being run against.
 *
 * <p>Spring Boot already migrates on startup and validates before it does, so an edited
 * migration (a checksum mismatch), a failed one or one missing from this build fails the
 * context. Hibernate's {@code ddl-auto=validate} then catches a table or column the entities
 * disagree with. What neither catches by default is a <em>future</em> migration: a version in
 * the database's history that this build does not have. Flyway ignores those unless told
 * otherwise ({@code ignoreMigrationPatterns} defaults to {@code *:future}), so an older image
 * rolled back onto a newer schema would start and run - and write rows a newer column's
 * constraints were never checked against.
 *
 * <p>Nothing is ignored here. A rollback across a migration therefore needs the pre-deploy
 * backup restored (DEPLOYMENT.md, "Rollback"), which is the point: that is a decision for a
 * person, not something to discover in production data later.
 *
 * <p>Set in code rather than through {@code spring.flyway.ignore-migration-patterns}: Spring
 * Boot skips an empty list, so the property cannot express "ignore nothing".
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayConfigurationCustomizer refuseUnrecognisedMigrations() {
        return configuration -> configuration
                .ignoreMigrationPatterns(new String[0])
                .validateOnMigrate(true)
                .outOfOrder(false)
                .baselineOnMigrate(false)
                .cleanDisabled(true);
    }
}
