package com.tutorspoint.tutor;

import com.tutorspoint.AbstractIntegrationTest;
import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.domain.User;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.JwtService;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.storage.TestFiles;
import org.hamcrest.Matchers;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The two rules that cannot be proved without the real filter chain and the real schema:
 * a tutor reaches only their own profile, and the public endpoint gives away neither a draft
 * nor a way to contact anybody off-platform.
 *
 * <p>Running against the migrated PostgreSQL container is doing real work here beyond the
 * assertions: {@code ddl-auto=validate} means every save below only succeeds if the entity and
 * {@code V5__tutor_profiles.sql} agree, column for column and constraint for constraint.
 *
 * <p>The reference codes used are the ones V4 seeds, so a profile is built out of the same
 * vocabulary a client would read from {@code /api/reference}.
 */
@Transactional
class TutorProfileIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String tutorToken;
    private String otherTutorToken;
    private String parentToken;
    private Long tutorId;
    private Long otherTutorId;

    @BeforeEach
    void createAccounts() {
        Tutor tutor = activated(new Tutor("profile.tutor@example.lk", hash(), "Kasun Perera", "+94773000001", Language.EN));
        Tutor otherTutor = activated(new Tutor("profile.other@example.lk", hash(), "Nimal Silva", "+94773000002", Language.SI));
        Parent parent = activated(new Parent("profile.parent@example.lk", hash(), "Niluka", "+94773000003", Language.EN));

        tutorId = tutor.getId();
        otherTutorId = otherTutor.getId();
        tutorToken = jwtService.issueAccessToken(tutor);
        otherTutorToken = jwtService.issueAccessToken(otherTutor);
        parentToken = jwtService.issueAccessToken(parent);
    }

    @Test
    @DisplayName("a tutor opening the wizard for the first time gets an empty draft listing every requirement")
    void firstReadReturnsAnEmptyDraft() throws Exception {
        mockMvc.perform(get("/api/tutors/me/profile").header("Authorization", bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tutorId").value(tutorId))
                .andExpect(jsonPath("$.data.fullName").value("Kasun Perera"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.complete").value(false))
                .andExpect(jsonPath("$.data.missingFields.length()").value(8));
    }

    @Test
    @DisplayName("the /me routes answer for the token holder, so two tutors never see each other's draft")
    void ownProfileIsScopedToTheToken() throws Exception {
        saveDraft(tutorToken, completeDraft());

        mockMvc.perform(get("/api/tutors/me/profile").header("Authorization", bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tutorId").value(tutorId))
                .andExpect(jsonPath("$.data.headline").value("A/L Chemistry in Nugegoda"));

        // Same URL, different token, different profile - and the second tutor's is untouched.
        mockMvc.perform(get("/api/tutors/me/profile").header("Authorization", bearer(otherTutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tutorId").value(otherTutorId))
                .andExpect(jsonPath("$.data.headline").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("one tutor cannot publish or unpublish another tutor's profile, because no route names one")
    void publishingActsOnlyOnTheCallersProfile() throws Exception {
        completeProfile(tutorToken);

        mockMvc.perform(post("/api/tutors/me/profile/publish").header("Authorization", bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        // The second tutor publishing publishes nothing of the first tutor's: their own profile
        // is empty, so they are refused for their own missing fields.
        mockMvc.perform(post("/api/tutors/me/profile/publish").header("Authorization", bearer(otherTutorToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROFILE_INCOMPLETE"));

        mockMvc.perform(get("/api/tutors/" + tutorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("a parent cannot reach the tutor-only profile routes")
    void roleRulesAreEnforced() throws Exception {
        mockMvc.perform(get("/api/tutors/me/profile").header("Authorization", bearer(parentToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/tutors/me/profile/publish").header("Authorization", bearer(parentToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the owner routes are closed to a caller with no token")
    void ownerRoutesRequireAToken() throws Exception {
        mockMvc.perform(get("/api/tutors/me/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("NOT_AUTHENTICATED"));

        mockMvc.perform(put("/api/tutors/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeDraft()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the public endpoint shows a published profile to a guest, with no email and no phone number")
    void publicProfileHidesContactDetails() throws Exception {
        completeProfile(tutorToken);
        publish(tutorToken);

        String body = mockMvc.perform(get("/api/tutors/" + tutorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tutorId").value(tutorId))
                .andExpect(jsonPath("$.data.fullName").value("Kasun Perera"))
                .andExpect(jsonPath("$.data.subjects[0].code").value("CHEMISTRY"))
                .andExpect(jsonPath("$.data.mediums[0].code").value("ENGLISH"))
                .andExpect(jsonPath("$.data.homeBaseArea.code").value("COLOMBO_NUGEGODA"))
                .andExpect(jsonPath("$.data.areasServed.length()").value(2))
                .andExpect(jsonPath("$.data.syllabuses.length()").value(2))
                .andExpect(jsonPath("$.data.qualifications[0].institution").value("University of Colombo"))
                .andExpect(jsonPath("$.data.verified").value(false))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.phoneNumber").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        // Not just absent under the names above: the values appear nowhere in the payload.
        assertThat(body).doesNotContain("profile.tutor@example.lk", "+94773000001");
    }

    @Test
    @DisplayName("a draft is invisible to the public, and indistinguishable from a tutor who does not exist")
    void publicEndpointHidesDrafts() throws Exception {
        saveDraft(tutorToken, completeDraft());

        mockMvc.perform(get("/api/tutors/" + tutorId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));

        // A tutor with no profile at all, and an id that is nobody's, answer the same way.
        mockMvc.perform(get("/api/tutors/" + otherTutorId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tutors/999999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an unpublished profile disappears from the public endpoint and keeps its content")
    void unpublishingTakesTheProfileDown() throws Exception {
        completeProfile(tutorToken);
        publish(tutorToken);
        mockMvc.perform(get("/api/tutors/" + tutorId)).andExpect(status().isOk());

        mockMvc.perform(post("/api/tutors/me/profile/unpublish").header("Authorization", bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNPUBLISHED"));

        mockMvc.perform(get("/api/tutors/" + tutorId)).andExpect(status().isNotFound());

        // Still there for its owner, and still complete, so going live again is one call.
        mockMvc.perform(get("/api/tutors/me/profile").header("Authorization", bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headline").value("A/L Chemistry in Nugegoda"))
                .andExpect(jsonPath("$.data.complete").value(true));

        mockMvc.perform(post("/api/tutors/me/profile/publish").header("Authorization", bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("saving a draft leaves a published profile published")
    void savingDoesNotUnpublish() throws Exception {
        completeProfile(tutorToken);
        publish(tutorToken);

        saveDraft(tutorToken, completeDraft().replace("A/L Chemistry in Nugegoda", "A/L Chemistry in Maharagama"));

        mockMvc.perform(get("/api/tutors/" + tutorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.headline").value("A/L Chemistry in Maharagama"));
    }

    @Test
    @DisplayName("an incomplete profile is refused, and the response says which fields are short")
    void publishingIsRefusedWhenIncomplete() throws Exception {
        saveDraft(tutorToken, """
                {
                  "headline": "A/L Chemistry",
                  "availableOnline": true,
                  "availabilityStatus": "ACCEPTING"
                }
                """);

        mockMvc.perform(get("/api/tutors/me/profile").header("Authorization", bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complete").value(false))
                .andExpect(jsonPath("$.data.missingFields").value(Matchers.hasItems(
                        "PHOTO", "BIO", "SUBJECTS", "EXAM_LEVELS", "MEDIUMS", "CLASS_FORMATS", "FEE_RANGE")))
                // Online availability alone satisfies the location requirement.
                .andExpect(jsonPath("$.data.missingFields").value(Matchers.not(Matchers.hasItem("LOCATION"))));

        mockMvc.perform(post("/api/tutors/me/profile/publish").header("Authorization", bearer(tutorToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROFILE_INCOMPLETE"));
    }

    @Test
    @DisplayName("a reference code that names nothing is rejected rather than silently dropped")
    void unknownReferenceCodesAreRejected() throws Exception {
        mockMvc.perform(put("/api/tutors/me/profile")
                        .header("Authorization", bearer(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectCodes": ["ASTROLOGY"],
                                  "availableOnline": true,
                                  "availabilityStatus": "ACCEPTING"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("a request the DTO cannot accept is rejected at the edge, in the caller's language")
    void invalidInputIsRejectedBeforeAnyBusinessLogic() throws Exception {
        mockMvc.perform(put("/api/tutors/me/profile")
                        .header("Authorization", bearer(tutorToken))
                        .header("Accept-Language", "si")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "yearsOfExperience": -3,
                                  "availableOnline": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors.availabilityStatus").exists())
                .andExpect(jsonPath("$.error.fieldErrors.yearsOfExperience").exists());
    }

    @Test
    @DisplayName("reference values come back in the language the caller asked for")
    void profileRendersInTheCallersLanguage() throws Exception {
        completeProfile(tutorToken);
        publish(tutorToken);

        mockMvc.perform(get("/api/tutors/" + tutorId).header("Accept-Language", "si"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediums[0].code").value("ENGLISH"))
                .andExpect(jsonPath("$.data.mediums[0].name").value("ඉංග්‍රීසි"));
    }

    /**
     * Everything publishing needs: the draft, and a photograph through the upload endpoint.
     *
     * <p>Two calls rather than one, because the photo is no longer a field on the draft - it is
     * a file the platform inspects, re-encodes and stores before the profile may point at it.
     */
    private void completeProfile(String token) throws Exception {
        saveDraft(token, completeDraft());
        uploadPhoto(token);
    }

    private void uploadPhoto(String token) throws Exception {
        mockMvc.perform(multipart("/api/tutors/me/profile/photo")
                        .file(new MockMultipartFile("file", "kasun.jpg", "image/jpeg", TestFiles.jpeg()))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private void saveDraft(String token, String body) throws Exception {
        mockMvc.perform(put("/api/tutors/me/profile")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private void publish(String token) throws Exception {
        mockMvc.perform(post("/api/tutors/me/profile/publish").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    /** Every requirement satisfied, in codes V4 seeds. */
    private static String completeDraft() {
        return """
                {
                  "headline": "A/L Chemistry in Nugegoda",
                  "bio": "Fifteen years preparing students for A/L Chemistry.",
                  "subjectCodes": ["CHEMISTRY"],
                  "examLevelCodes": ["GCE_AL"],
                  "syllabusCodes": ["NATIONAL_ENGLISH", "CAMBRIDGE"],
                  "areaServedCodes": ["COLOMBO_NUGEGODA", "COLOMBO_MAHARAGAMA"],
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
                  "availabilityStatus": "LIMITED"
                }
                """;
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
