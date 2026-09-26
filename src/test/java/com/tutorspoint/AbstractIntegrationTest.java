package com.tutorspoint;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for every integration test (`*IT`).
 * <p>
 * Boots the full application context against a real PostgreSQL container with the
 * Flyway migrations applied, so {@code ddl-auto=validate} genuinely proves the
 * entities match the versioned schema. Subclasses get an authenticated-or-not
 * {@link MockMvc} that runs through the real Spring Security filter chain.
 * <p>
 * The container is a JVM-wide singleton started once in a static initialiser rather
 * than managed by {@code @Testcontainers}: that extension stops a static container in
 * {@code afterAll} of each test class, which would leave the cached Spring context
 * pointing at a dead port as soon as a second {@code *IT} runs. Ryuk reaps it when the
 * JVM exits.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;
}
