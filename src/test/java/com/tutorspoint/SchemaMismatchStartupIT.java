package com.tutorspoint;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The application refuses to start on a schema it does not match, rather than running on it.
 *
 * <p>Each case migrates a scratch database in the shared container with this build's own
 * migrations, tampers with it the way a real mismatch arises, and boots the whole application
 * against it. The first case is the control: without it, every failure below could be a
 * context that never starts for some unrelated reason.
 */
class SchemaMismatchStartupIT {

    private static final String DATABASE = "startup_guard_check";

    @BeforeEach
    void migrateAScratchDatabase() throws SQLException {
        adminExecute("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)");
        adminExecute("CREATE DATABASE " + DATABASE);
        Flyway.configure()
                .dataSource(jdbcUrl(), username(), password())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @AfterEach
    void dropTheScratchDatabase() throws SQLException {
        adminExecute("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)");
    }

    @Test
    @DisplayName("control: an untouched, fully migrated database starts")
    void matchingSchemaStarts() {
        try (ConfigurableApplicationContext context = start()) {
            assertThat(context.isRunning()).isTrue();
        }
    }

    @Test
    @DisplayName("a migration from a newer release in the history - a rollback - refuses to start")
    void futureMigrationRefusesToStart() throws SQLException {
        execute("""
                INSERT INTO flyway_schema_history (installed_rank, version, description, type, script,
                                                   checksum, installed_by, execution_time, success)
                SELECT max(installed_rank) + 1, '999', 'from a newer release', 'SQL',
                       'V999__from_a_newer_release.sql', 0, current_user, 0, TRUE
                FROM flyway_schema_history
                """);

        assertThatThrownBy(this::start).hasRootCauseInstanceOf(FlywayValidateException.class);
    }

    @Test
    @DisplayName("an applied migration edited after it ran refuses to start")
    void editedMigrationRefusesToStart() throws SQLException {
        execute("UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '2'");

        assertThatThrownBy(this::start).hasRootCauseInstanceOf(FlywayValidateException.class);
    }

    @Test
    @DisplayName("a table changed outside Flyway refuses to start")
    void driftedTableRefusesToStart() throws SQLException {
        execute("ALTER TABLE users DROP COLUMN password_hash");

        assertThatThrownBy(this::start).hasRootCauseInstanceOf(SchemaManagementException.class);
    }

    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(TutorspointBackendApplication.class)
                .profiles("test")
                .run("--spring.datasource.url=" + jdbcUrl(),
                        "--spring.datasource.username=" + username(),
                        "--spring.datasource.password=" + password(),
                        "--server.port=0");
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(), username(), password());
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /** Against the container's default database: a database cannot be dropped from inside itself. */
    private static void adminExecute(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                AbstractIntegrationTest.POSTGRES.getJdbcUrl(), username(), password());
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String jdbcUrl() {
        String base = AbstractIntegrationTest.POSTGRES.getJdbcUrl();
        return base.substring(0, base.lastIndexOf('/') + 1) + DATABASE;
    }

    private static String username() {
        return AbstractIntegrationTest.POSTGRES.getUsername();
    }

    private static String password() {
        return AbstractIntegrationTest.POSTGRES.getPassword();
    }
}
