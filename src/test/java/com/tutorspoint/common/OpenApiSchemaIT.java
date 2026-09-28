package com.tutorspoint.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorspoint.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.ManagedType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The published API contract carries no internal field.
 *
 * <p>The frontend generates its types from this document, so a field that appears here is a
 * field the client is told exists. Read from the live {@code /v3/api-docs}, which is built from
 * the same classes the controllers return, so a DTO that grows an internal field fails here.
 */
class OpenApiSchemaIT extends AbstractIntegrationTest {

    /**
     * Persistence and credential internals, named as they are on the entities. None has any
     * business in a schema, request or response.
     */
    private static final Set<String> NEVER_EXPOSED = Set.of(
            "passwordHash", "codeHash", "tokenHash", "storageKey", "attemptCount",
            "consumedAt", "revokedAt", "deleted", "deletedAt", "version",
            "createdBy", "updatedBy", "verificationNotes", "internalNotes");

    /** Passwords travel in, never out: only the requests that set one may name one. */
    private static final Map<String, Set<String>> ALLOWED_ONLY_IN = Map.of(
            "password", Set.of("LoginRequest", "RegisterRequest"),
            "newPassword", Set.of("ResetPasswordRequest"),
            // A reviewer's note on a document is shown to the admin console and to the tutor who
            // uploaded it - a rejection has to say what to fix. Nowhere else: not on a profile, not
            // in search.
            "reviewNotes", Set.of("AdminDocumentResponse", "VerificationDocumentResponse"));

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private JsonNode schemas;

    @BeforeEach
    void readTheContract() throws Exception {
        String document = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        schemas = objectMapper.readTree(document).path("components").path("schemas");
        assertThat(schemas.size()).as("schemas in the document").isGreaterThan(20);
    }

    @Test
    @DisplayName("no schema has a persistence or credential field, and passwords appear only where they are set")
    void noInternalFields() {
        Map<String, Set<String>> properties = propertiesBySchema();
        List<String> problems = new ArrayList<>();

        properties.forEach((schema, fields) -> {
            fields.stream()
                    .filter(NEVER_EXPOSED::contains)
                    .forEach(field -> problems.add(schema + "." + field));
            ALLOWED_ONLY_IN.forEach((field, allowedIn) -> {
                if (fields.contains(field) && !allowedIn.contains(schema)) {
                    problems.add(schema + "." + field + " (allowed only in " + allowedIn + ")");
                }
            });
        });

        assertThat(problems).as("internal fields in the published schemas").isEmpty();
    }

    @Test
    @DisplayName("no entity is published as a schema: every request and response is a DTO")
    void noEntityIsASchema() {
        Set<String> entityNames = entityManagerFactory.getMetamodel().getManagedTypes().stream()
                .map(ManagedType::getJavaType)
                .map(Class::getSimpleName)
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> published = new TreeSet<>();
        schemas.fieldNames().forEachRemaining(published::add);
        published.retainAll(entityNames);

        assertThat(published).as("schemas named after entities").isEmpty();
    }

    /** Property names per schema, including those nested inline in another schema. */
    private Map<String, Set<String>> propertiesBySchema() {
        Map<String, Set<String>> result = new TreeMap<>();
        schemas.properties().forEach(entry -> collect(entry.getKey(), entry.getValue(), result));
        return result;
    }

    private static void collect(String schema, JsonNode node, Map<String, Set<String>> into) {
        if (node.isObject()) {
            node.path("properties").properties().forEach(property ->
                    into.computeIfAbsent(schema, ignored -> new TreeSet<>()).add(property.getKey()));
        }
        node.forEach(child -> collect(schema, child, into));
    }
}
