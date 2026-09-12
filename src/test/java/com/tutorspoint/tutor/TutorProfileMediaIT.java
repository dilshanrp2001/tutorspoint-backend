package com.tutorspoint.tutor;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * The profile photograph, end to end: uploaded by its owner, cleaned on the way in, and served
 * to anybody from a public URL.
 *
 * <p>Against the real {@code LocalDiskFileStorage} writing to a temporary directory, because
 * the claims worth making here are about a file that genuinely exists - that the bytes served
 * back are the sanitised ones rather than the uploaded ones, and that a document key handed to
 * the media endpoint does not produce a file at all.
 */
@Transactional
class TutorProfileMediaIT extends AbstractIntegrationTest {

    private static final String GPS_MARKER = "GPS-6.9271-79.8612-HOME-ADDRESS";

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

    @BeforeEach
    void createAccounts() {
        Tutor tutor = activated(new Tutor("media.tutor@example.lk", hash(), "Kasun Perera", "+94774000001", Language.EN));
        Parent parent = activated(new Parent("media.parent@example.lk", hash(), "Niluka", "+94774000002", Language.EN));
        tutorToken = jwtService.issueAccessToken(tutor);
        parentToken = jwtService.issueAccessToken(parent);
    }

    @Test
    @DisplayName("an uploaded photo is stored, and the profile points at a public URL for it")
    void uploadingAPhotoSetsThePublicUrl() throws Exception {
        String photoUrl = uploadPhoto(TestFiles.jpeg());

        assertThat(photoUrl).startsWith("/api/media/photos/").endsWith(".jpg");

        mockMvc.perform(get("/api/tutors/me/profile").header("Authorization", bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photoUrl").value(photoUrl))
                .andExpect(jsonPath("$.data.missingFields", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("PHOTO"))));
    }

    @Test
    @DisplayName("the photograph is served to a guest, with no token and the right content type")
    void thePhotoIsPublic() throws Exception {
        String photoUrl = uploadPhoto(TestFiles.jpeg());

        byte[] served = mockMvc.perform(get(photoUrl))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(served).isNotEmpty();
    }

    @Test
    @DisplayName("the GPS position a phone writes into a photograph is not what gets published")
    void exifIsStrippedBeforeAnythingIsServed() throws Exception {
        byte[] fromAPhone = TestFiles.jpegWithExif(GPS_MARKER);
        assertThat(asText(fromAPhone)).contains(GPS_MARKER);

        String photoUrl = uploadPhoto(fromAPhone);

        byte[] served = mockMvc.perform(get(photoUrl))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        // The file on the platform is a re-encode, so the tutor's home coordinates are not in
        // it - not merely absent from the response, absent from what was stored.
        assertThat(asText(served)).doesNotContain(GPS_MARKER);
        assertThat(asText(served)).doesNotContain("Exif");
    }

    @Test
    @DisplayName("replacing a photo leaves the old URL serving nothing")
    void replacingAPhotoRemovesTheOldFile() throws Exception {
        String first = uploadPhoto(TestFiles.jpeg());
        String second = uploadPhoto(TestFiles.png());

        assertThat(second).isNotEqualTo(first);
        mockMvc.perform(get(second)).andExpect(status().isOk());
        mockMvc.perform(get(first)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("removing the photo takes the file with it and makes the profile incomplete")
    void removingThePhotoDeletesTheFile() throws Exception {
        String photoUrl = uploadPhoto(TestFiles.jpeg());

        mockMvc.perform(delete("/api/tutors/me/profile/photo").header("Authorization", bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photoUrl").doesNotExist())
                .andExpect(jsonPath("$.data.missingFields", org.hamcrest.Matchers.hasItem("PHOTO")));

        mockMvc.perform(get(photoUrl)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a PDF renamed to .jpg is not a profile photo")
    void refusesAFileThatIsNotAnImage() throws Exception {
        mockMvc.perform(multipart("/api/tutors/me/profile/photo")
                        .file(new MockMultipartFile("file", "portrait.jpg", "image/jpeg", TestFiles.pdf()))
                        .header("Authorization", bearer(tutorToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UPLOAD_TYPE_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("a photo over five megabytes is refused")
    void refusesAnOversizedPhoto() throws Exception {
        mockMvc.perform(multipart("/api/tutors/me/profile/photo")
                        .file(new MockMultipartFile("file", "huge.jpg", "image/jpeg",
                                TestFiles.jpegOfAtLeast(5 * 1024 * 1024 + 1)))
                        .header("Authorization", bearer(tutorToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UPLOAD_TOO_LARGE"));
    }

    @Test
    @DisplayName("an empty part is refused before anything is looked at")
    void refusesAnEmptyUpload() throws Exception {
        mockMvc.perform(multipart("/api/tutors/me/profile/photo")
                        .file(new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]))
                        .header("Authorization", bearer(tutorToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UPLOAD_EMPTY"));
    }

    @Test
    @DisplayName("a parent cannot upload a tutor profile photo")
    void onlyATutorMayUpload() throws Exception {
        mockMvc.perform(multipart("/api/tutors/me/profile/photo")
                        .file(new MockMultipartFile("file", "x.jpg", "image/jpeg", TestFiles.jpeg()))
                        .header("Authorization", bearer(parentToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/tutors/me/profile/photo")
                        .file(new MockMultipartFile("file", "x.jpg", "image/jpeg", TestFiles.jpeg())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an intro video is accepted as MP4 and refused as anything else")
    void introVideoAcceptsOnlyMp4() throws Exception {
        mockMvc.perform(multipart("/api/tutors/me/profile/intro-video")
                        .file(new MockMultipartFile("file", "intro.mp4", "video/mp4", TestFiles.jpeg()))
                        .header("Authorization", bearer(tutorToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UPLOAD_TYPE_NOT_ALLOWED"));

        mockMvc.perform(multipart("/api/tutors/me/profile/intro-video")
                        .file(new MockMultipartFile("file", "intro.mp4", "video/mp4", TestFiles.mp4()))
                        .header("Authorization", bearer(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.introVideoUrl").value(org.hamcrest.Matchers.startsWith("/api/media/videos/")));
    }

    @Test
    @DisplayName("the media endpoint is not a way to read a private file")
    void theMediaEndpointRefusesPrivateAreas() throws Exception {
        // A well-formed key that names the documents area. It is answered exactly as a key that
        // names nothing: this endpoint must not confirm that somebody's NIC scan exists.
        mockMvc.perform(get("/api/media/documents/2026/09/3f1b0c62-9d0e-4a3f-8b1a-6f2d4c7e5a90.pdf"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/media/photos/2026/09/3f1b0c62-9d0e-4a3f-8b1a-6f2d4c7e5a90.jpg"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a traversal attempt through the media endpoint reaches nothing")
    void theMediaEndpointRefusesTraversal() throws Exception {
        // Refused by Spring Security's request firewall before any handler runs - a layer
        // earlier than the key pattern, which would have refused it too. Asserted as 400
        // rather than 404 because that is what actually happens, and a test that claimed
        // otherwise would quietly stop describing the system.
        mockMvc.perform(get("/api/media/../../../../etc/passwd"))
                .andExpect(status().isBadRequest());

        // Past the firewall, and stopped by the key pattern instead: well-formed path, not a
        // key this platform could have issued.
        mockMvc.perform(get("/api/media/photos/2026/09/not-a-key.jpg"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/media/photos/2026/09/3f1b0c62-9d0e-4a3f-8b1a-6f2d4c7e5a90.exe"))
                .andExpect(status().isNotFound());
    }

    /** Uploads a photo and returns the public URL the profile now carries. */
    private String uploadPhoto(byte[] bytes) throws Exception {
        String body = mockMvc.perform(multipart("/api/tutors/me/profile/photo")
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes))
                        .header("Authorization", bearer(tutorToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).at("/data/photoUrl").asText();
    }

    private static String asText(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
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
