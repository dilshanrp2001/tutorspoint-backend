package com.tutorspoint;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * V10 against data that existed before it — the one thing the other integration tests cannot
 * show, because they only ever see a schema migrated from empty in one go.
 *
 * <p>Migrates a throwaway schema in the shared container to V9, writes a parent with an enquiry
 * and a shortlist entry the way the V9 application would have, then applies V10 and checks
 * that the parent is now a seeker and still owns both, and that the new foreign keys still
 * refuse a tutor in a seeker's place. No Spring context: this is the migration on its own.
 */
class StudentRoleMigrationIT {

    private static final String SCHEMA = "v10_migration_check";

    private Connection connection;

    @BeforeEach
    void migrateAScratchSchemaToV9() throws SQLException {
        flyway("9").clean();
        flyway("9").migrate();
        connection = DriverManager.getConnection(AbstractIntegrationTest.POSTGRES.getJdbcUrl(),
                AbstractIntegrationTest.POSTGRES.getUsername(), AbstractIntegrationTest.POSTGRES.getPassword());
        connection.setSchema(SCHEMA);
    }

    @AfterEach
    void dropTheScratchSchema() throws SQLException {
        connection.close();
        flyway("latest").clean();
    }

    @Test
    @DisplayName("an existing parent's enquiry and shortlist survive V10, now owned by a seeker")
    void existingParentDataSurvives() throws SQLException {
        long parentId = insertUser("PARENT", "old.parent@example.lk", "+94770009001");
        execute("INSERT INTO parents (id) VALUES (" + parentId + ")");
        long tutorId = insertUser("TUTOR", "old.tutor@example.lk", "+94770009002");
        execute("INSERT INTO tutors (id) VALUES (" + tutorId + ")");
        execute("""
                INSERT INTO enquiries (created_at, updated_at, parent_id, tutor_id, subject_id, exam_level_id,
                                       preferred_format, online)
                VALUES (now(), now(), %d, %d, (SELECT min(id) FROM subjects), (SELECT min(id) FROM exam_levels),
                        'ONLINE', TRUE)
                """.formatted(parentId, tutorId));
        execute("INSERT INTO shortlists (created_at, updated_at, parent_id, tutor_id, note) VALUES (now(), now(), %d, %d, 'kept')"
                .formatted(parentId, tutorId));

        flyway("latest").migrate();

        assertThat(count("SELECT count(*) FROM seekers WHERE id = " + parentId)).isOne();
        assertThat(count("SELECT count(*) FROM parents WHERE id = " + parentId)).isOne();
        assertThat(count("SELECT count(*) FROM enquiries WHERE seeker_id = " + parentId)).isOne();
        assertThat(count("SELECT count(*) FROM shortlists WHERE seeker_id = %d AND note = 'kept'".formatted(parentId))).isOne();

        // The new keys are real: a tutor is not a seeker, so it cannot own a shortlist.
        assertThatExceptionOfType(SQLException.class).isThrownBy(() ->
                execute("INSERT INTO shortlists (created_at, updated_at, seeker_id, tutor_id) VALUES (now(), now(), %d, %d)"
                        .formatted(tutorId, tutorId)));

        // And a student can now exist beside the parent.
        long studentId = insertUser("STUDENT", "new.student@example.lk", "+94770009003");
        execute("INSERT INTO seekers (id) VALUES (" + studentId + ")");
        execute("INSERT INTO students (id) VALUES (" + studentId + ")");
        assertThat(count("SELECT count(*) FROM users WHERE role = 'STUDENT'")).isOne();
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(AbstractIntegrationTest.POSTGRES.getJdbcUrl(),
                        AbstractIntegrationTest.POSTGRES.getUsername(), AbstractIntegrationTest.POSTGRES.getPassword())
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .target(target)
                .cleanDisabled(false)
                .load();
    }

    private long insertUser(String role, String email, String phone) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet keys = statement.executeQuery("""
                     INSERT INTO users (created_at, updated_at, role, email, password_hash, full_name, phone_number)
                     VALUES (now(), now(), '%s', '%s', 'hash', 'Someone', '%s') RETURNING id
                     """.formatted(role, email, phone))) {
            keys.next();
            return keys.getLong(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long count(String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
