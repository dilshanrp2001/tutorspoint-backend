package com.tutorspoint.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorspoint.AbstractIntegrationTest;
import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.domain.User;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.JwtService;
import com.tutorspoint.common.domain.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization, proved rather than asserted: a signed-in user holding a genuine token still
 * cannot reach anybody else's data.
 *
 * <p>The accounts are created through the repository and the tokens minted directly, because
 * what is under test is the authorization layer, not the registration flow — {@link AuthFlowIT}
 * covers that. Real tokens through the real filter chain, only a shorter way in.
 *
 * <p>Transactional so each method starts from the same three accounts: MockMvc runs in-process
 * on the test thread, so the requests join the test transaction and it is rolled back after.
 */
@Transactional
class AccountAuthorizationIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private String tutorToken;
    private String parentToken;
    private String otherParentToken;
    private Long tutorId;
    private Long parentId;

    @BeforeEach
    void createThreeAccounts() {
        Tutor tutor = activated(new Tutor("auth.tutor@example.lk", hash(), "Kasun", "+94772000001", Language.EN));
        Parent parent = activated(new Parent("auth.parent@example.lk", hash(), "Kamal", "+94772000002", Language.SI));
        Parent otherParent = activated(new Parent("auth.other@example.lk", hash(), "Sunil", "+94772000003", Language.TA));

        tutorId = tutor.getId();
        parentId = parent.getId();
        tutorToken = jwtService.issueAccessToken(tutor);
        parentToken = jwtService.issueAccessToken(parent);
        otherParentToken = jwtService.issueAccessToken(otherParent);
    }

    @Test
    @DisplayName("a tutor holding a valid token reads its own account and only its own")
    void aTutorCannotReadAnotherUsersAccount() throws Exception {
        // There is no account id anywhere in the request, so the only account a token can
        // name is its own. Two different tokens, two different answers from one URL.
        mockMvc.perform(get("/api/account").header("Authorization", bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(tutorId))
                .andExpect(jsonPath("$.data.email").value("auth.tutor@example.lk"))
                .andExpect(jsonPath("$.data.role").value("TUTOR"));

        mockMvc.perform(get("/api/account").header("Authorization", bearer(parentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(parentId))
                .andExpect(jsonPath("$.data.email").value("auth.parent@example.lk"));

        assertThat(tutorId).isNotEqualTo(parentId);
    }

    @Test
    @DisplayName("a tutor cannot touch the parent-only child profiles")
    void roleRulesAreEnforcedAtTheMethodLevel() throws Exception {
        mockMvc.perform(get("/api/account/children").header("Authorization", bearer(tutorToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("one parent cannot read, edit or delete another parent's child")
    void childProfilesAreScopedToTheirOwner() throws Exception {
        String created = mockMvc.perform(post("/api/account/children")
                        .header("Authorization", bearer(parentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Sanduni",
                                  "grade": "Grade 11",
                                  "examLevel": "GCE O/L",
                                  "school": "Visakha Vidyalaya"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Sanduni"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        long childId = objectMapper.readTree(created).at("/data/id").asLong();

        mockMvc.perform(get("/api/account/children").header("Authorization", bearer(otherParentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        // Not 403: the owner-scoped query cannot find it, so the other parent learns only that
        // no such child of theirs exists.
        mockMvc.perform(put("/api/account/children/" + childId)
                        .header("Authorization", bearer(otherParentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Renamed",
                                  "grade": "Grade 1",
                                  "examLevel": "Grade 5 Scholarship"
                                }
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/account/children/" + childId)
                        .header("Authorization", bearer(otherParentToken)))
                .andExpect(status().isNotFound());

        // Still the owner's, untouched.
        mockMvc.perform(get("/api/account/children").header("Authorization", bearer(parentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Sanduni"));
    }

    @Test
    @DisplayName("a protected endpoint refuses a missing, malformed or forged token")
    void unusableTokensAreRefusedWithTheStandardEnvelope() throws Exception {
        mockMvc.perform(get("/api/account"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_AUTHENTICATED"));

        mockMvc.perform(get("/api/account").header("Authorization", bearer("not-a-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("ACCESS_TOKEN_INVALID"));

        // Right shape, wrong signature: signed with a key this application does not hold.
        String forged = "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ0dXRvcnNwb2ludCIsInN1YiI6IjEiLCJlbWFpbCI6"
                + "ImF0dGFja2VyQGV4YW1wbGUubGsiLCJyb2xlIjoiQURNSU4ifQ.0000000000000000000000000000000000000000000";
        mockMvc.perform(get("/api/account").header("Authorization", bearer(forged)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("ACCESS_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("a deleted account keeps its row and stops being usable")
    void deletingIsSoftAndImmediate() throws Exception {
        mockMvc.perform(delete("/api/account").header("Authorization", bearer(parentToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/account").header("Authorization", bearer(parentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELETED"));

        assertThat(users.findById(parentId)).isPresent();

        // The access token outlives the deletion by design — nothing can revoke a stateless
        // token — but the entity refuses every change, so the window is read-only and short.
        mockMvc.perform(put("/api/account")
                        .header("Authorization", bearer(parentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Changed\", \"preferredLanguage\": \"EN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_DELETED"));
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
