package com.tutorspoint.common;

import com.tutorspoint.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the test harness end to end: the context starts against a real PostgreSQL,
 * Flyway migrates it, Hibernate validates the entities against it, and the application
 * reports itself healthy over HTTP without authentication.
 */
class HealthCheckIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("context loads against a Flyway-migrated PostgreSQL")
    void contextLoads() {
        // The base class booting the context is the assertion.
    }

    @Test
    @DisplayName("GET /actuator/health is public and reports UP")
    void healthEndpointReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
