package com.tutorspoint.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorspoint.AbstractIntegrationTest;
import com.tutorspoint.auth.domain.Admin;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Qualification documents through the real filter chain, the real store and the migrated
 * schema (FR-T7).
 *
 * <p>The rule this class exists for is privacy. A document is the most sensitive thing on the
 * platform, so the tests assert not only that the owner and an administrator can read one, but
 * that everybody else is answered as though it did not exist, that no listing hands out a
 * storage key, and that the public media endpoint is not a second way in.
 */
@Transactional
class VerificationDocumentIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private String tutorToken;
    private String otherTutorToken;
    private String parentToken;
    private String adminToken;

    @BeforeEach
    void createAccounts() {
        Tutor tutor = activated(new Tutor("doc.tutor@example.lk", hash(), "Kasun Perera", "+94775000001", Language.EN));
        Tutor otherTutor = activated(new Tutor("doc.other@example.lk", hash(), "Nimal Silva", "+94775000002", Language.SI));
        Parent parent = activated(new Parent("doc.parent@example.lk", hash(), "Niluka", "+94775000003", Language.EN));
        Admin admin = activated(new Admin("doc.admin@tutorspoint.lk", hash(), "Reviewer", "+94775000004", Language.EN));

        tutorToken = jwtService.issueAccessToken(tutor);
        otherTutorToken = jwtService.issueAccessToken(otherTutor);
        parentToken = jwtService.issueAccessToken(parent);
        adminToken = jwtService.issueAccessToken(admin);
    }

    @Test
    @DisplayName("a tutor submits a document and sees it pending in their own list")
    void submitAndList() throws Exception {
        long documentId = upload(tutorToken, "DEGREE_CERTIFICATE", "my degree.pdf", TestFiles.pdf());

        mockMvc.perform(get("/api/tutors/me/documents").header("Authorization", bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(documentId))
                .andExpect(jsonPath("$.data[0].documentType").value("DEGREE_CERTIFICATE"))
                .andExpect(jsonPath("$.data[0].originalFilename").value("my degree.pdf"))
                .andExpect(jsonPath("$.data[0].contentType").value("application/pdf"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].reviewedAt").doesNotExist());
    }

    @Test
    @DisplayName("no listing ever hands out a storage key or a URL to the file")
    void theKeyIsNeverExposed() throws Exception {
        upload(tutorToken, "NIC", "nic.jpg", TestFiles.jpeg());

        String body = mockMvc.perform(get("/api/tutors/me/documents").header("Authorization", bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].storageKey").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        // Not merely absent under that name: nothing in the payload is a key or a media URL.
        assertThat(body).doesNotContain("documents/", "/api/media/");
    }

    @Test
    @DisplayName("one tutor never sees another tutor's documents")
    void listsAreScopedToTheirOwner() throws Exception {
        upload(tutorToken, "NIC", "nic.jpg", TestFiles.jpeg());

        mockMvc.perform(get("/api/tutors/me/documents").header("Authorization", bearer(otherTutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("the owner downloads their own document, as an attachment that is never cached")
    void theOwnerCanDownload() throws Exception {
        long documentId = upload(tutorToken, "DEGREE_CERTIFICATE", "my degree.pdf", TestFiles.pdf());

        byte[] downloaded = mockMvc.perform(get("/api/documents/" + documentId)
                        .header("Authorization", bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(downloaded).isEqualTo(TestFiles.pdf());
    }

    @Test
    @DisplayName("an administrator downloads any document, because that is the review")
    void anAdministratorCanDownload() throws Exception {
        long documentId = upload(tutorToken, "NIC", "nic.jpg", TestFiles.jpeg());

        mockMvc.perform(get("/api/documents/" + documentId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"));
    }

    @Test
    @DisplayName("a non-owner is answered as though the document did not exist")
    void aNonOwnerCannotDownload() throws Exception {
        long documentId = upload(tutorToken, "NIC", "nic.jpg", TestFiles.jpeg());

        // Not 403. A stranger must not be able to learn that document 41 is somebody's NIC.
        mockMvc.perform(get("/api/documents/" + documentId).header("Authorization", bearer(otherTutorToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/documents/" + documentId).header("Authorization", bearer(parentToken)))
                .andExpect(status().isNotFound());

        // The same answer a made-up id gets, which is what makes the two indistinguishable.
        mockMvc.perform(get("/api/documents/999999").header("Authorization", bearer(otherTutorToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a guest cannot reach a document at all")
    void aGuestCannotDownload() throws Exception {
        long documentId = upload(tutorToken, "NIC", "nic.jpg", TestFiles.jpeg());

        mockMvc.perform(get("/api/documents/" + documentId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("NOT_AUTHENTICATED"));
    }

    @Test
    @DisplayName("a file that is not PDF, JPEG or PNG is refused whatever it is called")
    void refusesAWrongContentType() throws Exception {
        mockMvc.perform(multipart("/api/tutors/me/documents")
                        .file(new MockMultipartFile("file", "degree.pdf", "application/pdf", TestFiles.mp4()))
                        .param("documentType", "DEGREE_CERTIFICATE")
                        .header("Authorization", bearer(tutorToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UPLOAD_TYPE_NOT_ALLOWED"));

        // And nothing was recorded.
        mockMvc.perform(get("/api/tutors/me/documents").header("Authorization", bearer(tutorToken)))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("a file over five megabytes is refused")
    void refusesAnOversizedFile() throws Exception {
        mockMvc.perform(multipart("/api/tutors/me/documents")
                        .file(new MockMultipartFile("file", "scan.jpg", "image/jpeg",
                                TestFiles.jpegOfAtLeast(5 * 1024 * 1024 + 1)))
                        .param("documentType", "NIC")
                        .header("Authorization", bearer(tutorToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UPLOAD_TOO_LARGE"));

        mockMvc.perform(get("/api/tutors/me/documents").header("Authorization", bearer(tutorToken)))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("a document type the platform does not have is a bad request, not a server error")
    void refusesAnUnknownDocumentType() throws Exception {
        mockMvc.perform(multipart("/api/tutors/me/documents")
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", TestFiles.pdf()))
                        .param("documentType", "PASSPORT")
                        .header("Authorization", bearer(tutorToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("a parent cannot submit qualification documents")
    void onlyATutorMaySubmit() throws Exception {
        mockMvc.perform(multipart("/api/tutors/me/documents")
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", TestFiles.pdf()))
                        .param("documentType", "OTHER")
                        .header("Authorization", bearer(parentToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("withdrawing a pending document removes the row and the file")
    void withdrawingAPendingDocument() throws Exception {
        long documentId = upload(tutorToken, "OTHER", "draft.pdf", TestFiles.pdf());

        mockMvc.perform(delete("/api/tutors/me/documents/" + documentId)
                        .header("Authorization", bearer(tutorToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tutors/me/documents").header("Authorization", bearer(tutorToken)))
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/documents/" + documentId).header("Authorization", bearer(tutorToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("one tutor cannot withdraw another tutor's document")
    void cannotWithdrawSomebodyElsesDocument() throws Exception {
        long documentId = upload(tutorToken, "NIC", "nic.jpg", TestFiles.jpeg());

        mockMvc.perform(delete("/api/tutors/me/documents/" + documentId)
                        .header("Authorization", bearer(otherTutorToken)))
                .andExpect(status().isNotFound());

        // Still the owner's, still readable by them.
        mockMvc.perform(get("/api/documents/" + documentId).header("Authorization", bearer(tutorToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a PDF and a photograph of a certificate are both accepted")
    void acceptsWhatATutorActuallyHas() throws Exception {
        upload(tutorToken, "DEGREE_CERTIFICATE", "degree.pdf", TestFiles.pdf());
        upload(tutorToken, "NIC", "nic.jpg", TestFiles.jpeg());
        upload(tutorToken, "TEACHING_CERTIFICATE", "cert.png", TestFiles.png());

        mockMvc.perform(get("/api/tutors/me/documents").header("Authorization", bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    /** Submits a document and returns its id. */
    private long upload(String token, String documentType, String filename, byte[] bytes) throws Exception {
        String body = mockMvc.perform(multipart("/api/tutors/me/documents")
                        .file(new MockMultipartFile("file", filename, "application/octet-stream", bytes))
                        .param("documentType", documentType)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).at("/data/id").asLong();
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
