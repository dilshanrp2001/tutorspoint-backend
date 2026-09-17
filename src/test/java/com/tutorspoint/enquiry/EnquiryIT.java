package com.tutorspoint.enquiry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorspoint.AbstractIntegrationTest;
import com.tutorspoint.auth.domain.Admin;
import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.Student;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.domain.User;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.JwtService;
import com.tutorspoint.common.audit.AuditAction;
import com.tutorspoint.common.audit.AuditLog;
import com.tutorspoint.common.audit.AuditLogRepository;
import com.tutorspoint.common.audit.AuditTargetType;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.storage.TestFiles;
import com.tutorspoint.enquiry.repository.EnquiryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The enquiry flow through the real filter chain, the real events and the migrated schema.
 *
 * <p>The rule this class exists for is masking. Everything else here — the thread, the
 * statuses, the inbox — is a means of getting the platform into the states where a contact
 * detail either may or may not come out, and then checking the whole response body rather than
 * one convenient field. A leak does not announce itself under the name {@code phoneNumber}.
 *
 * <p>Deliberately not {@code @Transactional}. The listeners are bound to {@code AFTER_COMMIT},
 * which is how FR-E3 is enforced, and a test that rolled its transaction back would silence
 * exactly the mechanism it is here to prove. Each test therefore uses its own accounts, and the
 * ids are read from the responses rather than assumed.
 */
class EnquiryIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meters;

    @Autowired
    private EnquiryRepository enquiries;

    @Autowired
    private AuditLogRepository auditLogs;

    /** Everything this test committed, in the order it has to come back out. */
    private final List<Long> createdAccountIds = new ArrayList<>();

    private String parentToken;
    private String tutorToken;
    private String otherParentToken;
    private String adminToken;
    private Long parentId;
    private Long tutorId;

    @BeforeEach
    void createAccountsAndPublishAProfile() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        Parent parent = activated(new Parent("enq.parent" + suffix + "@example.lk", hash(),
                "Niluka Fernando", phone(suffix, 1), Language.EN));
        Parent otherParent = activated(new Parent("enq.other" + suffix + "@example.lk", hash(),
                "Dilani Jayawardena", phone(suffix, 2), Language.EN));
        Tutor tutor = activated(new Tutor("enq.tutor" + suffix + "@example.lk", hash(),
                "Kasun Perera", phone(suffix, 3), Language.EN));
        Admin admin = activated(new Admin("enq.admin" + suffix + "@tutorspoint.lk", hash(),
                "Reviewer", phone(suffix, 4), Language.EN));

        parentId = parent.getId();
        tutorId = tutor.getId();
        parentToken = jwtService.issueAccessToken(parent);
        otherParentToken = jwtService.issueAccessToken(otherParent);
        tutorToken = jwtService.issueAccessToken(tutor);
        adminToken = jwtService.issueAccessToken(admin);

        createdAccountIds.addAll(List.of(admin.getId(), parent.getId(), otherParent.getId(), tutor.getId()));

        publishProfile(tutorToken);
    }

    /**
     * Undoes what the test committed.
     *
     * <p>Needed precisely because this class does not roll back. A published tutor profile left
     * behind here is a profile {@code TutorSearchIT} then counts, and a test that quietly
     * changes another test's arithmetic is worse than no test at all. Threads go first: the
     * enquiry-to-account foreign keys deliberately do not cascade, so an account cannot be
     * removed while a conversation still refers to it.
     */
    @AfterEach
    void removeWhatWasCommitted() {
        createdAccountIds.forEach(id ->
                enquiries.deleteAll(enquiries.findBySeekerIdOrderByCreatedAtDesc(id, Pageable.unpaged()).getContent()));
        // The tutor's profile and photo row go with the account: V5 cascades from tutors.
        createdAccountIds.forEach(users::deleteById);
    }

    @Test
    @DisplayName("a parent enquires, and nothing in the response is a way to contact anybody")
    void theFirstResponseCarriesNoContactDetails() throws Exception {
        String body = enquire(parentToken, "Do you teach A/L Chemistry on weekends?")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.contactRevealed").value(false))
                .andExpect(jsonPath("$.data.contact").doesNotExist())
                .andExpect(jsonPath("$.data.messages.length()").value(1))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // Not merely absent under that name: nothing in the payload is an address or a number.
        assertThat(body).doesNotContain("@example.lk", "+9477");
    }

    @Test
    @DisplayName("a phone number in the first message is replaced, in Sri Lankan formats")
    void theFirstMessageIsScrubbed() throws Exception {
        String body = enquire(parentToken,
                "Please call me on 077 123 4567 or +94112345678, or mail niluka@gmail.com")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body)
                .doesNotContain("077 123 4567", "0771234567", "+94112345678", "niluka@gmail.com")
                .contains("removed");
    }

    @Test
    @DisplayName("a non-participant cannot read the thread, and is told it does not exist")
    void nonParticipantsCannotReadAThread() throws Exception {
        long enquiryId = idOf(enquire(parentToken, "Do you teach on weekends?"));

        // Another parent: not forbidden, not found. The distinction matters — a 403 would
        // confirm the thread is there.
        mockMvc.perform(get("/api/enquiries/" + enquiryId).header("Authorization", bearer(otherParentToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));

        // An administrator is not a participant either. Moderation has its own audited route.
        mockMvc.perform(get("/api/enquiries/" + enquiryId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());

        // And a guest gets 401, which is what the frontend turns into a login prompt.
        mockMvc.perform(get("/api/enquiries/" + enquiryId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a non-participant cannot write to the thread either")
    void nonParticipantsCannotPostToAThread() throws Exception {
        long enquiryId = idOf(enquire(parentToken, "Do you teach on weekends?"));

        mockMvc.perform(post("/api/enquiries/" + enquiryId + "/messages")
                        .header("Authorization", bearer(otherParentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Hello\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("contact details stay masked until the tutor replies, then both sides see the other's")
    void contactDetailsAppearOnlyAfterTheTutorReplies() throws Exception {
        long enquiryId = idOf(enquire(parentToken, "Do you teach A/L Chemistry?"));

        // The tutor has read it but not answered. Still masked — for the tutor too.
        String beforeReply = mockMvc.perform(get("/api/enquiries/" + enquiryId)
                        .header("Authorization", bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VIEWED"))
                .andExpect(jsonPath("$.data.contactRevealed").value(false))
                .andExpect(jsonPath("$.data.contact").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(beforeReply).doesNotContain("@example.lk", "+9477");

        reply(tutorToken, enquiryId, "Yes, Saturday mornings are free")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("RESPONDED"))
                .andExpect(jsonPath("$.data.contactRevealed").value(true))
                // The tutor, having answered, is given the parent's details.
                .andExpect(jsonPath("$.data.contact.fullName").value("Niluka Fernando"));

        // And the parent is given the tutor's — not their own, and nobody else's.
        mockMvc.perform(get("/api/enquiries/" + enquiryId).header("Authorization", bearer(parentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactRevealed").value(true))
                .andExpect(jsonPath("$.data.contact.fullName").value("Kasun Perera"))
                .andExpect(jsonPath("$.data.contact.email").exists())
                .andExpect(jsonPath("$.data.contact.phoneNumber").exists());
    }

    @Test
    @DisplayName("the reveal is on the audit record once - on the tutor's first reply, not the second (NFR-10)")
    void theRevealIsAudited() throws Exception {
        long enquiryId = idOf(enquire(parentToken, "Do you teach A/L Chemistry?"));
        assertThat(auditLogs.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(AuditTargetType.ENQUIRY, enquiryId))
                .isEmpty();

        reply(tutorToken, enquiryId, "Yes, Saturday mornings are free").andExpect(status().isCreated());
        reply(tutorToken, enquiryId, "Or Sunday afternoons").andExpect(status().isCreated());

        assertThat(auditLogs.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(AuditTargetType.ENQUIRY, enquiryId))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getAction()).isEqualTo(AuditAction.CONTACT_REVEALED);
                    assertThat(row.getActorId()).isEqualTo(tutorId);
                    assertThat(row.getAfterValue()).containsEntry("contactRevealed", true);
                    assertThat(row.getIpAddress()).isEqualTo("127.0.0.1");
                })
                .extracting(AuditLog::getCreatedAt).isNotNull();
    }

    @Test
    @DisplayName("once the channel is open, a phone number is no longer stripped")
    void scrubbingStopsOnceContactIsRevealed() throws Exception {
        long enquiryId = idOf(enquire(parentToken, "Do you teach A/L Chemistry?"));
        reply(tutorToken, enquiryId, "Yes, Saturday mornings are free");

        reply(parentToken, enquiryId, "Great — my number is 0771234567")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.messages[2].body").value("Great — my number is 0771234567"));
    }

    @Test
    @DisplayName("no inbox listing hands out a contact detail, revealed or not")
    void listingsNeverCarryContactDetails() throws Exception {
        long enquiryId = idOf(enquire(parentToken, "Do you teach A/L Chemistry?"));
        reply(tutorToken, enquiryId, "Yes, Saturday mornings are free");

        for (String token : new String[]{parentToken, tutorToken}) {
            String body = mockMvc.perform(get("/api/enquiries").header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.enquiries[0].id").value(enquiryId))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(body).doesNotContain("@example.lk", "+9477", "\"contact\"");
        }
    }

    @Test
    @DisplayName("the inbox is role-aware: a parent sees sent, a tutor sees received")
    void theInboxIsRoleAware() throws Exception {
        long enquiryId = idOf(enquire(parentToken, "Do you teach A/L Chemistry?"));

        mockMvc.perform(get("/api/enquiries").header("Authorization", bearer(parentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.enquiries[0].id").value(enquiryId))
                .andExpect(jsonPath("$.data.enquiries[0].tutorId").value(tutorId));

        mockMvc.perform(get("/api/enquiries").header("Authorization", bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enquiries[0].seekerId").value(parentId))
                // The tutor has not opened it, so the parent's opening message is unread.
                .andExpect(jsonPath("$.data.enquiries[0].unreadCount").value(1));

        // The other parent's inbox is empty: they sent nothing and received nothing.
        mockMvc.perform(get("/api/enquiries").header("Authorization", bearer(otherParentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        // Filtering by a status nothing is in returns nothing, not everything.
        mockMvc.perform(get("/api/enquiries").param("status", "CLOSED")
                        .header("Authorization", bearer(parentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("the unread count covers every thread: what the other side wrote, until it is read")
    void theUnreadCountFollowsReading() throws Exception {
        long enquiryId = idOf(enquire(parentToken, "Do you teach A/L Chemistry?"));
        assertUnread(tutorToken, 1);
        // Your own message is never unread to you.
        assertUnread(parentToken, 0);
        assertUnread(otherParentToken, 0);

        mockMvc.perform(get("/api/enquiries/" + enquiryId).header("Authorization", bearer(tutorToken)))
                .andExpect(status().isOk());
        assertUnread(tutorToken, 0);

        reply(tutorToken, enquiryId, "Yes, Saturday mornings are free").andExpect(status().isCreated());
        reply(tutorToken, enquiryId, "Or Sunday afternoons").andExpect(status().isCreated());
        assertUnread(parentToken, 2);
        assertUnread(tutorToken, 0);
    }

    @Test
    @DisplayName("the event listeners fire: both counters move, and only on the first reply")
    void theEventListenersFire() throws Exception {
        double sentBefore = counter("tutorspoint.enquiries.sent");
        double respondedBefore = counter("tutorspoint.enquiries.responded");

        long enquiryId = idOf(enquire(parentToken, "Do you teach A/L Chemistry?"));
        assertThat(counter("tutorspoint.enquiries.sent")).isEqualTo(sentBefore + 1);
        assertThat(counter("tutorspoint.enquiries.responded")).isEqualTo(respondedBefore);

        reply(tutorToken, enquiryId, "Yes, Saturday mornings are free");
        assertThat(counter("tutorspoint.enquiries.responded")).isEqualTo(respondedBefore + 1);

        // A second reply is an ordinary message and counts for nothing.
        reply(tutorToken, enquiryId, "Shall we say nine o'clock?");
        assertThat(counter("tutorspoint.enquiries.responded")).isEqualTo(respondedBefore + 1);
    }

    @Test
    @DisplayName("a second live enquiry to the same tutor is refused; a closed thread frees the pair")
    void onlyOneLiveThreadPerTutor() throws Exception {
        long enquiryId = idOf(enquire(parentToken, "Do you teach A/L Chemistry?"));

        enquire(parentToken, "Are you there?")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ENQUIRY_ALREADY_OPEN"));

        mockMvc.perform(post("/api/enquiries/" + enquiryId + "/close")
                        .header("Authorization", bearer(parentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        enquire(parentToken, "A new question, months later").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a closed thread takes no further messages")
    void closedThreadsAreClosed() throws Exception {
        long enquiryId = idOf(enquire(parentToken, "Do you teach A/L Chemistry?"));
        mockMvc.perform(post("/api/enquiries/" + enquiryId + "/close")
                .header("Authorization", bearer(parentToken))).andExpect(status().isOk());

        reply(parentToken, enquiryId, "One more thing")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ENQUIRY_CLOSED"));
    }

    @Test
    @DisplayName("a tutor cannot open an enquiry, whatever they post")
    void onlySeekersMaySend() throws Exception {
        enquire(tutorToken, "Hello").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a student enquires for themselves, sees it in their inbox, and is answered like a parent")
    void aStudentIsAFullSeeker() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        Student student = activated(new Student("enq.student" + suffix + "@example.lk", hash(),
                "Ashan Wijesinghe", phone(suffix, 5), Language.EN));
        createdAccountIds.add(student.getId());
        String studentToken = jwtService.issueAccessToken(student);

        long enquiryId = idOf(enquire(studentToken, "Do you teach A/L Chemistry?")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.seekerId").value(student.getId()))
                .andExpect(jsonPath("$.data.seekerName").value("Ashan Wijesinghe"))
                .andExpect(jsonPath("$.data.child").doesNotExist()));

        mockMvc.perform(get("/api/enquiries").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enquiries.length()").value(1))
                .andExpect(jsonPath("$.data.enquiries[0].id").value(enquiryId));

        reply(tutorToken, enquiryId, "Yes, on Saturdays.").andExpect(status().isCreated());
        assertUnread(studentToken, 1);
        mockMvc.perform(get("/api/enquiries/" + enquiryId).header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactRevealed").value(true))
                .andExpect(jsonPath("$.data.contact.fullName").value("Kasun Perera"));
    }

    @Test
    @DisplayName("a student naming a child is refused: a student has none (FR-A8)")
    void aStudentCannotNameAChild() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        Student student = activated(new Student("enq.student" + suffix + "@example.lk", hash(),
                "Ashan Wijesinghe", phone(suffix, 6), Language.EN));
        createdAccountIds.add(student.getId());

        mockMvc.perform(post("/api/enquiries")
                        .header("Authorization", bearer(jwtService.issueAccessToken(student)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tutorId": %d,
                                  "childProfileId": 1,
                                  "subjectCode": "CHEMISTRY",
                                  "examLevelCode": "GCE_AL",
                                  "preferredFormat": "ONLINE",
                                  "online": true,
                                  "message": "For my brother"
                                }
                                """.formatted(tutorId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CHILD_PROFILE_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("an enquiry names an area or online, and the request is rejected if it names both")
    void thePlaceMustBeExactlyOneAnswer() throws Exception {
        mockMvc.perform(post("/api/enquiries")
                        .header("Authorization", bearer(parentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tutorId": %d,
                                  "subjectCode": "CHEMISTRY",
                                  "examLevelCode": "GCE_AL",
                                  "preferredFormat": "ONLINE",
                                  "preferredAreaCode": "COLOMBO_NUGEGODA",
                                  "online": true,
                                  "message": "Do you teach online?"
                                }
                                """.formatted(tutorId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ENQUIRY_PLACE_REQUIRED"));
    }

    @Test
    @DisplayName("an empty message is rejected at the edge, before any of this runs")
    void anEmptyMessageIsRejected() throws Exception {
        enquire(parentToken, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    private ResultActions enquire(String token, String message) throws Exception {
        return mockMvc.perform(post("/api/enquiries")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "tutorId": %d,
                          "subjectCode": "CHEMISTRY",
                          "examLevelCode": "GCE_AL",
                          "preferredFormat": "SMALL_GROUP",
                          "preferredAreaCode": "COLOMBO_NUGEGODA",
                          "online": false,
                          "message": %s
                        }
                        """.formatted(tutorId, objectMapper.writeValueAsString(message))));
    }

    private void assertUnread(String token, long expected) throws Exception {
        mockMvc.perform(get("/api/enquiries/unread-count").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(expected));
    }

    private ResultActions reply(String token, long enquiryId, String body) throws Exception {
        return mockMvc.perform(post("/api/enquiries/" + enquiryId + "/messages")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.createObjectNode().put("body", body).toString()));
    }

    private long idOf(ResultActions result) throws Exception {
        JsonNode response = objectMapper.readTree(
                result.andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        return response.path("data").path("id").asLong();
    }

    private double counter(String name) {
        return meters.find(name).counter().count();
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
        // A profile is not publishable without a photograph, so the wizard is completed here
        // in full rather than half-filled: what this class needs is a tutor a parent could
        // actually have found in the search results.
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

    /** Unique per run, so tests that commit do not collide on the phone number's unique index. */
    private static String phone(String suffix, int slot) {
        String tail = suffix.substring(suffix.length() - 7);
        return "+947%d%s".formatted(slot, tail);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
