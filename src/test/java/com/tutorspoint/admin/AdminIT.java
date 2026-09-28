package com.tutorspoint.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorspoint.AbstractIntegrationTest;
import com.tutorspoint.auth.RefreshTokenService;
import com.tutorspoint.auth.domain.Admin;
import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.domain.User;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.JwtService;
import com.tutorspoint.common.audit.AuditAction;
import com.tutorspoint.common.audit.AuditLog;
import com.tutorspoint.common.audit.AuditLogRepository;
import com.tutorspoint.common.audit.AuditTargetType;
import com.tutorspoint.common.config.TimeConfig;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.AuthenticationFailedException;
import com.tutorspoint.common.storage.TestFiles;
import com.tutorspoint.search.repository.SearchDailyCountRepository;
import com.tutorspoint.tutor.domain.TutorProfile;
import com.tutorspoint.tutor.repository.TutorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin API through the real filter chain, the real audit listener and the migrated schema.
 *
 * <p>Two things are proved here that no unit test can. That nobody but an administrator reaches
 * any of it - checked at the route, with real tokens. And that every decision leaves exactly one
 * row in {@code audit_logs}, written in the same transaction, with the reason and the client
 * address, in a table the database itself refuses to let anybody edit.
 *
 * <p>{@code @Transactional}: the audit listener runs inside the action's transaction rather than
 * after commit, so a rolled-back test still sees every row it caused, and leaves none behind.
 */
@Transactional
class AdminIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private TutorProfileRepository profiles;

    @Autowired
    private AuditLogRepository auditLogs;

    @Autowired
    private SearchDailyCountRepository searchCounts;

    @Autowired
    private RefreshTokenService refreshTokens;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private Tutor tutor;
    private Parent parent;
    private Admin admin;
    private String tutorToken;
    private String parentToken;
    private String adminToken;

    @BeforeEach
    void createAccounts() {
        tutor = activated(new Tutor("adm.tutor@example.lk", hash(), "Kasun Perera", "+94776100001", Language.EN));
        parent = activated(new Parent("adm.parent@example.lk", hash(), "Niluka Fernando", "+94776100002", Language.EN));
        admin = activated(new Admin("adm.ops@tutorspoint.lk", hash(), "Ops Reviewer", "+94776100003", Language.EN));
        tutorToken = jwtService.issueAccessToken(tutor);
        parentToken = jwtService.issueAccessToken(parent);
        adminToken = jwtService.issueAccessToken(admin);
    }

    // ---------------------------------------------------------------------
    // Who may reach it
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("no admin route answers a guest, a parent or a tutor")
    void onlyAdministrators() throws Exception {
        List<String> routes = List.of("/api/admin/users", "/api/admin/metrics", "/api/admin/tutors/" + tutor.getId());
        for (String route : routes) {
            mockMvc.perform(get(route)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(route).header("Authorization", bearer(parentToken))).andExpect(status().isForbidden());
            mockMvc.perform(get(route).header("Authorization", bearer(tutorToken))).andExpect(status().isForbidden());
            mockMvc.perform(get(route).header("Authorization", bearer(adminToken))).andExpect(status().isOk());
        }
        // A tutor cannot verify themselves, which is the one they would most like to.
        mockMvc.perform(put("/api/admin/tutors/" + tutor.getId() + "/verification")
                        .header("Authorization", bearer(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"verified\": true}"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // Accounts
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("the account list filters by role, status and a fragment of name, email or phone")
    void listsAndFilters() throws Exception {
        mockMvc.perform(get("/api/admin/users").param("q", "adm.").param("role", "PARENT")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.users[0].email").value("adm.parent@example.lk"))
                .andExpect(jsonPath("$.data.users[0].phoneNumber").value("+94776100002"))
                .andExpect(jsonPath("$.data.users[0].passwordHash").doesNotExist());

        mockMvc.perform(get("/api/admin/users").param("q", "KASUN").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.data.users[*].email", org.hamcrest.Matchers.hasItem("adm.tutor@example.lk")));

        mockMvc.perform(get("/api/admin/users").param("q", "+9477610000").param("status", "SUSPENDED")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.data.total").value(0));

        // A wildcard typed into the box is a character, not "everything".
        mockMvc.perform(get("/api/admin/users").param("q", "%").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("only tutors with a document waiting are in the review queue")
    void pendingDocumentsQueue() throws Exception {
        String queue = "/api/admin/users";
        mockMvc.perform(get(queue).param("pendingDocuments", "true").param("q", "adm.")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.data.total").value(0));

        uploadDocument();

        mockMvc.perform(get(queue).param("pendingDocuments", "true").param("q", "adm.")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.users[0].id").value(tutor.getId()));
    }

    @Test
    @DisplayName("suspending an account blocks it, ends its sessions, and is audited with reason and address")
    void suspendsAnAccount() throws Exception {
        String refreshToken = refreshTokens.issueFor(parent.getId());

        mockMvc.perform(post("/api/admin/users/" + parent.getId() + "/suspend")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Sent the same message to forty tutors\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        assertThatExceptionOfType(AuthenticationFailedException.class)
                .isThrownBy(() -> refreshTokens.requireActive(refreshToken));

        AuditLog row = onlyAuditRow(AuditTargetType.USER, parent.getId());
        assertThat(row.getAction()).isEqualTo(AuditAction.ACCOUNT_SUSPENDED);
        assertThat(row.getActorId()).isEqualTo(admin.getId());
        assertThat(row.getReason()).isEqualTo("Sent the same message to forty tutors");
        assertThat(row.getBeforeValue()).containsEntry("status", "ACTIVE");
        assertThat(row.getAfterValue()).containsEntry("status", "SUSPENDED");
        assertThat(row.getIpAddress()).isEqualTo("127.0.0.1");

        mockMvc.perform(post("/api/admin/users/" + parent.getId() + "/reinstate")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        assertThat(auditRows(AuditTargetType.USER, parent.getId()))
                .extracting(AuditLog::getAction)
                .containsExactlyInAnyOrder(AuditAction.ACCOUNT_SUSPENDED, AuditAction.ACCOUNT_REINSTATED);
    }

    @Test
    @DisplayName("a suspension needs a reason, and an administrator cannot be suspended at all")
    void suspensionGuards() throws Exception {
        mockMvc.perform(post("/api/admin/users/" + parent.getId() + "/suspend")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors.reason").exists());

        mockMvc.perform(post("/api/admin/users/" + admin.getId() + "/suspend")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"lockout\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ADMIN_ACCOUNT_NOT_MODERATABLE"));

        mockMvc.perform(post("/api/admin/users/" + parent.getId() + "/reinstate")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_SUSPENDED"));

        assertThat(auditRows(AuditTargetType.USER, parent.getId())).isEmpty();
        assertThat(auditRows(AuditTargetType.USER, admin.getId())).isEmpty();
    }

    // ---------------------------------------------------------------------
    // Tutor review
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("the review screen: the account, the profile and every document; 404 for a non-tutor")
    void tutorDetail() throws Exception {
        profiles.saveAndFlush(new TutorProfile(tutor));
        uploadDocument();

        mockMvc.perform(get("/api/admin/tutors/" + tutor.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.fullName").value("Kasun Perera"))
                .andExpect(jsonPath("$.data.profile.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.profile.verified").value(false))
                .andExpect(jsonPath("$.data.documents.length()").value(1))
                .andExpect(jsonPath("$.data.documents[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.documents[0].storageKey").doesNotExist());

        mockMvc.perform(get("/api/admin/tutors/" + parent.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("reviewing documents: opening one, approving, rejecting with a reason - each on the record")
    void reviewsDocuments() throws Exception {
        long first = uploadDocument();
        long second = uploadDocument();

        mockMvc.perform(get("/api/documents/" + first).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        assertThat(onlyAuditRow(AuditTargetType.VERIFICATION_DOCUMENT, first).getAction())
                .isEqualTo(AuditAction.DOCUMENT_VIEWED);

        mockMvc.perform(post("/api/admin/documents/" + first + "/approve").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewedByName").value("Ops Reviewer"));

        mockMvc.perform(post("/api/admin/documents/" + first + "/reject")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"notes\": \"too late\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DOCUMENT_ALREADY_REVIEWED"));

        mockMvc.perform(post("/api/admin/documents/" + second + "/reject")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors.notes").exists());

        mockMvc.perform(post("/api/admin/documents/" + second + "/reject")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"The certificate number is not legible\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        // The tutor sees the reason, and not who gave it.
        mockMvc.perform(get("/api/tutors/me/documents").header("Authorization", bearer(tutorToken)))
                .andExpect(jsonPath("$.data[?(@.id == " + second + ")].reviewNotes")
                        .value("The certificate number is not legible"))
                .andExpect(jsonPath("$.data[0].reviewedByName").doesNotExist());

        assertThat(auditRows(AuditTargetType.VERIFICATION_DOCUMENT, first)).extracting(AuditLog::getAction)
                .containsExactlyInAnyOrder(AuditAction.DOCUMENT_VIEWED, AuditAction.DOCUMENT_APPROVED);
        assertThat(onlyAuditRow(AuditTargetType.VERIFICATION_DOCUMENT, second).getAfterValue())
                .containsEntry("status", "REJECTED")
                .containsEntry("reviewNotes", "The certificate number is not legible");
    }

    @Test
    @DisplayName("the verified badge: granted, re-granted as a no-op, withdrawn - two rows, not three")
    void togglesVerification() throws Exception {
        TutorProfile profile = profiles.saveAndFlush(new TutorProfile(tutor));

        setVerified(true).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.verified").value(true))
                .andExpect(jsonPath("$.data.profile.verifiedAt").exists());
        setVerified(true).andExpect(status().isOk());
        setVerified(false).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.verified").value(false))
                .andExpect(jsonPath("$.data.profile.verifiedAt").doesNotExist());

        assertThat(auditRows(AuditTargetType.TUTOR_PROFILE, profile.getId())).extracting(AuditLog::getAction)
                .containsExactlyInAnyOrder(AuditAction.TUTOR_VERIFIED, AuditAction.TUTOR_VERIFICATION_REVOKED);
    }

    @Test
    @DisplayName("a suspended profile is frozen for its tutor, and comes back as a draft")
    void moderatesAProfile() throws Exception {
        TutorProfile profile = profiles.saveAndFlush(new TutorProfile(tutor));
        String suspend = "/api/admin/tutors/" + tutor.getId() + "/profile/suspend";

        mockMvc.perform(post(suspend).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Claims a degree that was not awarded\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.status").value("SUSPENDED"));

        mockMvc.perform(put("/api/tutors/me/profile").header("Authorization", bearer(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\": \"Edited my way out\", \"availabilityStatus\": \"ACCEPTING\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROFILE_SUSPENDED"));

        mockMvc.perform(post("/api/admin/tutors/" + tutor.getId() + "/profile/reinstate")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.status").value("DRAFT"));

        mockMvc.perform(post("/api/admin/tutors/" + tutor.getId() + "/profile/reinstate")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROFILE_NOT_SUSPENDED"));

        assertThat(auditRows(AuditTargetType.TUTOR_PROFILE, profile.getId()))
                .extracting(AuditLog::getAction, AuditLog::getReason)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(AuditAction.PROFILE_SUSPENDED, "Claims a degree that was not awarded"),
                        org.assertj.core.groups.Tuple.tuple(AuditAction.PROFILE_REINSTATED, null));
    }

    // ---------------------------------------------------------------------
    // Metrics
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("metrics count registrations and searches in the window, from the tables, not memory")
    void metrics() throws Exception {
        JsonNode before = fetchMetrics();

        activated(new Tutor("adm.new.tutor@example.lk", hash(), "New Tutor", "+94776100011", Language.EN));
        activated(new Parent("adm.new.parent@example.lk", hash(), "New Parent", "+94776100012", Language.EN));
        LocalDate today = LocalDate.now(TimeConfig.PLATFORM_ZONE);
        searchCounts.increment(today);
        searchCounts.increment(today);

        JsonNode after = fetchMetrics();
        assertThat(after.at("/registrations/tutors").asLong() - before.at("/registrations/tutors").asLong()).isEqualTo(1);
        assertThat(after.at("/registrations/parents").asLong() - before.at("/registrations/parents").asLong()).isEqualTo(1);
        assertThat(after.at("/searches").asLong() - before.at("/searches").asLong()).isEqualTo(2);

        // A window that ended before today counts none of it.
        mockMvc.perform(get("/api/admin/metrics").param("from", "2020-01-01").param("to", "2020-01-31")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registrations.total").value(0))
                .andExpect(jsonPath("$.data.searches").value(0))
                .andExpect(jsonPath("$.data.responseRate").doesNotExist());

        mockMvc.perform(get("/api/admin/metrics").param("from", "2026-09-10").param("to", "2026-09-01")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------
    // The record itself
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("the audit table refuses an update and a delete, from anywhere")
    void theAuditTrailIsAppendOnly() throws Exception {
        TutorProfile profile = profiles.saveAndFlush(new TutorProfile(tutor));
        setVerified(true).andExpect(status().isOk());
        Long rowId = onlyAuditRow(AuditTargetType.TUTOR_PROFILE, profile.getId()).getId();

        // The last statement in this test: a refused statement aborts the transaction.
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET reason = 'rewritten' WHERE id = ?", rowId))
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("the search counter is one row per day, however many searches")
    void searchCounterUpserts() {
        LocalDate day = LocalDate.of(2031, 1, 1);
        searchCounts.increment(day);
        searchCounts.increment(day);
        searchCounts.increment(day);

        assertThat(searchCounts.sumBetween(day, day)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM search_daily_counts WHERE day = ?", Long.class, day))
                .isEqualTo(1);
    }

    // ---------------------------------------------------------------------

    private ResultActions setVerified(boolean verified) throws Exception {
        return mockMvc.perform(put("/api/admin/tutors/" + tutor.getId() + "/verification")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"verified\": " + verified + "}"));
    }

    private JsonNode fetchMetrics() throws Exception {
        String body = mockMvc.perform(get("/api/admin/metrics").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    private long uploadDocument() throws Exception {
        String body = mockMvc.perform(multipart("/api/tutors/me/documents")
                        .file(new MockMultipartFile("file", "degree.pdf", "application/pdf", TestFiles.pdf()))
                        .param("documentType", "DEGREE_CERTIFICATE")
                        .header("Authorization", bearer(tutorToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private List<AuditLog> auditRows(AuditTargetType type, Long id) {
        return auditLogs.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(type, id);
    }

    private AuditLog onlyAuditRow(AuditTargetType type, Long id) {
        List<AuditLog> rows = auditRows(type, id);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private <T extends User> T activated(T user) {
        user.verifyEmail();
        user.verifyPhone();
        user.activate();
        return users.saveAndFlush(user);
    }

    private String hash() {
        return passwordEncoder.encode("Colombo2026");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
