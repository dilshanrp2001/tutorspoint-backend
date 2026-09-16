package com.tutorspoint.enquiry;

import com.tutorspoint.AbstractIntegrationTest;
import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.domain.User;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.JwtService;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.storage.TestFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shortlists through the real filter chain and the migrated schema (FR-P1).
 *
 * <p>Two things are worth proving here that a unit test cannot. One parent's shortlist is
 * invisible to another, including the private note. And a shortlist entry — which carries a
 * whole tutor card — still hands out no way to contact anybody, because a saved tutor is not a
 * contacted one.
 *
 * <p>{@code @Transactional} here, unlike {@code EnquiryIT}: nothing in this flow depends on a
 * commit, so each test rolls back and leaves the database as it found it.
 */
@Transactional
class ShortlistIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String parentToken;
    private String otherParentToken;
    private String tutorToken;
    private Long tutorId;

    @BeforeEach
    void createAccountsAndPublishAProfile() throws Exception {
        Parent parent = activated(new Parent("short.parent@example.lk", hash(),
                "Niluka Fernando", "+94774000001", Language.EN));
        Parent otherParent = activated(new Parent("short.other@example.lk", hash(),
                "Dilani Jayawardena", "+94774000002", Language.EN));
        Tutor tutor = activated(new Tutor("short.tutor@example.lk", hash(),
                "Kasun Perera", "+94774000003", Language.EN));

        tutorId = tutor.getId();
        parentToken = jwtService.issueAccessToken(parent);
        otherParentToken = jwtService.issueAccessToken(otherParent);
        tutorToken = jwtService.issueAccessToken(tutor);

        publishProfile(tutorToken);
    }

    @Test
    @DisplayName("a saved tutor comes back with the search card, the note, and no contact detail")
    void savingReturnsTheCardAndNothingPrivate() throws Exception {
        save(parentToken, "Cheaper, but a 40 minute drive")
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/shortlist").header("Authorization", bearer(parentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].tutorId").value(tutorId))
                .andExpect(jsonPath("$.data[0].tutorName").value("Kasun Perera"))
                .andExpect(jsonPath("$.data[0].note").value("Cheaper, but a 40 minute drive"))
                .andExpect(jsonPath("$.data[0].tutor.headline").value("A/L Chemistry in Nugegoda"))
                .andExpect(jsonPath("$.data[0].tutor.subjects.length()").value(1))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).doesNotContain("@example.lk", "+9477");
    }

    @Test
    @DisplayName("saving the same tutor twice rewrites the note rather than adding a second entry")
    void savingTwiceIsAnUpsert() throws Exception {
        save(parentToken, "First thoughts").andExpect(status().isOk());
        save(parentToken, "Second thoughts").andExpect(status().isOk());

        mockMvc.perform(get("/api/shortlist").header("Authorization", bearer(parentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].note").value("Second thoughts"));
    }

    @Test
    @DisplayName("a save with no body at all is a save with no note")
    void theNoteIsOptional() throws Exception {
        mockMvc.perform(post("/api/shortlist/" + tutorId).header("Authorization", bearer(parentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.note").doesNotExist());
    }

    @Test
    @DisplayName("one parent's shortlist, and their note, is invisible to another parent")
    void shortlistsAreScopedToTheirOwner() throws Exception {
        save(parentToken, "My own reminder").andExpect(status().isOk());

        mockMvc.perform(get("/api/shortlist").header("Authorization", bearer(otherParentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("removing takes the tutor off my list only, and removing twice is fine")
    void removingIsOwnerScopedAndIdempotent() throws Exception {
        save(parentToken, null).andExpect(status().isOk());
        save(otherParentToken, null).andExpect(status().isOk());

        mockMvc.perform(delete("/api/shortlist/" + tutorId).header("Authorization", bearer(parentToken)))
                .andExpect(status().isOk());
        // Again: the caller wanted them gone, and they are.
        mockMvc.perform(delete("/api/shortlist/" + tutorId).header("Authorization", bearer(parentToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/shortlist").header("Authorization", bearer(parentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        // The other parent still has theirs.
        mockMvc.perform(get("/api/shortlist").header("Authorization", bearer(otherParentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("a tutor has no shortlist, and a guest has no route to one")
    void onlyParentsHaveAShortlist() throws Exception {
        mockMvc.perform(get("/api/shortlist").header("Authorization", bearer(tutorToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/shortlist"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a tutor who does not exist cannot be shortlisted")
    void unknownTutorsCannotBeSaved() throws Exception {
        mockMvc.perform(post("/api/shortlist/999999").header("Authorization", bearer(parentToken)))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions save(String token, String note) throws Exception {
        String body = note == null ? "{}" : "{\"note\":\"" + note + "\"}";
        return mockMvc.perform(post("/api/shortlist/" + tutorId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void publishProfile(String token) throws Exception {
        mockMvc.perform(put("/api/tutors/me/profile")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "headline": "A/L Chemistry in Nugegoda",
                                  "bio": "Fifteen years preparing students for A/L Chemistry.",
                                  "subjectCodes": ["CHEMISTRY"],
                                  "examLevelCodes": ["GCE_AL"],
                                  "syllabusCodes": ["NATIONAL_ENGLISH"],
                                  "areaServedCodes": ["COLOMBO_NUGEGODA"],
                                  "homeBaseAreaCode": "COLOMBO_NUGEGODA",
                                  "mediums": ["ENGLISH"],
                                  "classFormats": ["SMALL_GROUP", "ONLINE"],
                                  "qualifications": [
                                    {"title": "BSc Chemistry", "institution": "University of Colombo", "yearAwarded": 2008}
                                  ],
                                  "yearsOfExperience": 15,
                                  "feeMin": 1500.00,
                                  "feeMax": 2500.00,
                                  "feeUnit": "PER_MONTH",
                                  "travelRadiusKm": 15,
                                  "availableOnline": true,
                                  "availabilityStatus": "ACCEPTING"
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/tutors/me/profile/photo")
                        .file(new MockMultipartFile("file", "kasun.jpg", "image/jpeg", TestFiles.jpeg()))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/tutors/me/profile/publish").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
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
