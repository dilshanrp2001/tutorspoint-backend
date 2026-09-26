package com.tutorspoint.tutor;

import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.CurrentUser;
import com.tutorspoint.common.domain.ClassFormat;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.domain.Medium;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.common.exception.InvalidUploadException;
import com.tutorspoint.common.exception.ResourceNotFoundException;
import com.tutorspoint.common.exception.UnauthorizedActionException;
import com.tutorspoint.common.storage.FileContent;
import com.tutorspoint.common.storage.FileStorage;
import com.tutorspoint.common.storage.FileType;
import com.tutorspoint.common.storage.ImageSanitiser;
import com.tutorspoint.common.storage.StorageArea;
import com.tutorspoint.common.storage.UploadedFile;
import com.tutorspoint.reference.ReferenceLabels;
import com.tutorspoint.reference.domain.Area;
import com.tutorspoint.reference.domain.ExamLevel;
import com.tutorspoint.reference.domain.Subject;
import com.tutorspoint.reference.domain.Syllabus;
import com.tutorspoint.reference.repository.AreaRepository;
import com.tutorspoint.reference.repository.ExamLevelRepository;
import com.tutorspoint.reference.repository.SubjectRepository;
import com.tutorspoint.reference.repository.SyllabusRepository;
import com.tutorspoint.tutor.domain.AvailabilityStatus;
import com.tutorspoint.tutor.domain.FeeUnit;
import com.tutorspoint.tutor.domain.ProfileStatus;
import com.tutorspoint.tutor.domain.TutorProfile;
import com.tutorspoint.tutor.dto.QualificationRequest;
import com.tutorspoint.tutor.dto.TutorProfileRequest;
import com.tutorspoint.tutor.repository.TutorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The service, with everything around it mocked: what is under test is the wiring, not the
 * rules. Whether a profile may be published is {@code TutorProfileTest}; whether the right
 * profile is loaded, the right codes resolved and the right entity method called is here.
 *
 * <p>The mapper is real. It is generated code with no collaborators of its own, and stubbing it
 * would leave the assertions unable to say anything about what a caller actually receives.
 */
@ExtendWith(MockitoExtension.class)
class TutorProfileServiceImplTest {

    private static final Long TUTOR_ID = 7L;
    private static final Long OTHER_ID = 8L;
    private static final String PHOTO_KEY = "photos/2026/09/3f1b0c62-9d0e-4a3f-8b1a-6f2d4c7e5a90.jpg";
    private static final String PHOTO_URL = "/api/media/" + PHOTO_KEY;

    @Mock
    private UserRepository users;

    @Mock
    private TutorProfileRepository profiles;

    @Mock
    private SubjectRepository subjects;

    @Mock
    private ExamLevelRepository examLevels;

    @Mock
    private SyllabusRepository syllabuses;

    @Mock
    private AreaRepository areas;

    @Mock
    private ReferenceLabels referenceLabels;

    @Mock
    private FileStorage fileStorage;

    @Mock
    private ImageSanitiser imageSanitiser;

    @Mock
    private CurrentUser currentUser;

    /** Real, not mocked: generated code with no collaborators, and stubbing it would leave
     * every assertion below unable to say what a caller actually receives. */
    private final TutorMapper tutorMapper = new TutorMapperImpl();

    private TutorProfileServiceImpl service;

    private Tutor tutor;

    @BeforeEach
    void setUp() {
        tutor = new Tutor("kasun@example.lk", "hash", "Kasun Perera", "+94771234567", Language.EN);
        service = new TutorProfileServiceImpl(users, profiles, subjects, examLevels, syllabuses, areas,
                tutorMapper, referenceLabels, fileStorage, imageSanitiser, currentUser);
        lenient().when(currentUser.requireId()).thenReturn(TUTOR_ID);
        lenient().when(referenceLabels.mediums(anyCollection(), any())).thenReturn(List.of());
        lenient().when(referenceLabels.classFormats(anyCollection(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("a tutor who has never opened the wizard gets a saved empty draft, not a 404")
    void createsTheDraftOnFirstRead() {
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.empty());
        when(users.findById(TUTOR_ID)).thenReturn(Optional.of(tutor));
        when(profiles.save(any(TutorProfile.class))).thenAnswer(call -> call.getArgument(0));

        var dto = service.myProfile(Language.EN);

        assertThat(dto.status()).isEqualTo(ProfileStatus.DRAFT);
        assertThat(dto.complete()).isFalse();
        assertThat(dto.missingFields()).contains("PHOTO", "BIO", "SUBJECTS", "LOCATION");
        verify(profiles).save(any(TutorProfile.class));
    }

    @Test
    @DisplayName("an existing profile is read, not replaced")
    void reusesAnExistingProfile() {
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.of(existingProfile()));

        var dto = service.myProfile(Language.EN);

        assertThat(dto.fullName()).isEqualTo("Kasun Perera");
        verify(profiles, never()).save(any());
    }

    @Test
    @DisplayName("the profile loaded is always the one belonging to the token, never one named in a request")
    void loadsTheCallersOwnProfile() {
        when(currentUser.requireId()).thenReturn(OTHER_ID);
        when(profiles.findByTutorId(OTHER_ID)).thenReturn(Optional.of(existingProfile()));

        service.myProfile(Language.EN);

        ArgumentCaptor<Long> loaded = ArgumentCaptor.forClass(Long.class);
        verify(profiles).findByTutorId(loaded.capture());
        assertThat(loaded.getValue()).isEqualTo(OTHER_ID);
    }

    @Test
    @DisplayName("an account that is not a tutor cannot be given a profile")
    void refusesANonTutorAccount() {
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.empty());
        when(users.findById(TUTOR_ID)).thenReturn(Optional.of(
                new Parent("kamal@example.lk", "hash", "Kamal", "+94772000002", Language.SI)));

        assertThatExceptionOfType(UnauthorizedActionException.class)
                .isThrownBy(() -> service.myProfile(Language.EN));
    }

    @Test
    @DisplayName("a saved draft resolves every code to its reference row and stays a draft")
    void savesTheDraft() {
        TutorProfile profile = profileWithPhoto();
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.of(profile));
        when(subjects.findByCodeInAndActiveTrue(Set.of("CHEMISTRY"))).thenReturn(List.of(subject()));
        when(examLevels.findByCodeInAndActiveTrue(Set.of("GCE_AL"))).thenReturn(List.of(examLevel()));
        when(syllabuses.findByCodeInAndActiveTrue(Set.of("NATIONAL_ENGLISH"))).thenReturn(List.of(syllabus()));
        when(areas.findByCodeInAndActiveTrue(Set.of("COLOMBO"))).thenReturn(List.of(area()));
        when(areas.findByCodeAndActiveTrue("COLOMBO")).thenReturn(Optional.of(area()));

        var dto = service.updateMyProfile(completeRequest(), Language.EN);

        assertThat(dto.status()).isEqualTo(ProfileStatus.DRAFT);
        assertThat(dto.complete()).isTrue();
        assertThat(dto.missingFields()).isEmpty();
        // By code, not by instance: an unsaved reference row has no id, and BaseEntity equality
        // is the id.
        assertThat(profile.getSubjects()).extracting(Subject::getCode).containsExactly("CHEMISTRY");
        assertThat(profile.getHomeBaseArea()).isNotNull();
        assertThat(profile.getQualifications()).hasSize(1);
        assertThat(profile.getFeeUnit()).isEqualTo(FeeUnit.PER_MONTH);
    }

    @Test
    @DisplayName("a code that names no active reference value is rejected, and named in the message")
    void rejectsAnUnknownCode() {
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.of(existingProfile()));
        when(subjects.findByCodeInAndActiveTrue(Set.of("ASTROLOGY"))).thenReturn(List.of());

        TutorProfileRequest request = requestWithSubjects(Set.of("ASTROLOGY"));

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.updateMyProfile(request, Language.EN))
                .withMessageContaining("ASTROLOGY");
    }

    @Test
    @DisplayName("saving never publishes, and never unpublishes")
    void savingDoesNotChangeVisibility() {
        TutorProfile profile = publishedProfile();
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.of(profile));
        when(subjects.findByCodeInAndActiveTrue(any())).thenReturn(List.of(subject()));
        when(examLevels.findByCodeInAndActiveTrue(any())).thenReturn(List.of(examLevel()));
        when(syllabuses.findByCodeInAndActiveTrue(any())).thenReturn(List.of(syllabus()));
        when(areas.findByCodeInAndActiveTrue(any())).thenReturn(List.of(area()));
        when(areas.findByCodeAndActiveTrue("COLOMBO")).thenReturn(Optional.of(area()));

        var dto = service.updateMyProfile(completeRequest(), Language.EN);

        assertThat(dto.status()).isEqualTo(ProfileStatus.PUBLISHED);
    }

    @Test
    @DisplayName("publishing an incomplete profile is refused by the entity, and the service does not soften it")
    void publishingIsRefusedWhenIncomplete() {
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.of(existingProfile()));

        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(() -> service.publishMyProfile(Language.EN))
                .satisfies(thrown -> assertThat(thrown.getCode()).isEqualTo("PROFILE_INCOMPLETE"));
    }

    @Test
    @DisplayName("publishing a complete profile makes it live")
    void publishesACompleteProfile() {
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.of(completeProfile()));

        var dto = service.publishMyProfile(Language.EN);

        assertThat(dto.status()).isEqualTo(ProfileStatus.PUBLISHED);
    }

    @Test
    @DisplayName("unpublishing takes a live profile down")
    void unpublishes() {
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.of(publishedProfile()));

        var dto = service.unpublishMyProfile(Language.EN);

        assertThat(dto.status()).isEqualTo(ProfileStatus.UNPUBLISHED);
    }

    @Test
    @DisplayName("a photo is re-encoded before it is stored, and the profile points at the stored file")
    void photoIsSanitisedThenStored() {
        TutorProfile profile = existingProfile();
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.of(profile));
        FileContent sanitised = new FileContent(FileType.JPEG, new byte[]{1, 2, 3});
        when(imageSanitiser.sanitise(any(FileContent.class))).thenReturn(sanitised);
        when(fileStorage.store(StorageArea.PROFILE_PHOTOS, sanitised)).thenReturn(PHOTO_KEY);

        var dto = service.uploadPhoto(new UploadedFile("holiday.jpg", jpegBytes()), Language.EN);

        assertThat(dto.photoUrl()).isEqualTo(PHOTO_URL);
        // The bytes that reach the store are the sanitiser's, never the ones that were sent.
        verify(fileStorage).store(StorageArea.PROFILE_PHOTOS, sanitised);
        assertThat(dto.missingFields()).doesNotContain("PHOTO");
    }

    @Test
    @DisplayName("replacing a photo deletes the file it replaced")
    void replacingAPhotoDeletesTheOldFile() {
        TutorProfile profile = profileWithPhoto();
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.of(profile));
        FileContent sanitised = new FileContent(FileType.JPEG, new byte[]{1, 2, 3});
        when(imageSanitiser.sanitise(any(FileContent.class))).thenReturn(sanitised);
        when(fileStorage.store(StorageArea.PROFILE_PHOTOS, sanitised))
                .thenReturn("photos/2026/09/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.jpg");

        service.uploadPhoto(new UploadedFile("newer.jpg", jpegBytes()), Language.EN);

        verify(fileStorage).delete(PHOTO_KEY);
    }

    @Test
    @DisplayName("a file that is not an image is refused before anything is stored")
    void refusesANonImagePhoto() {
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.of(existingProfile()));

        UploadedFile pdf = new UploadedFile("certificate.pdf", pdfBytes());

        assertThatExceptionOfType(InvalidUploadException.class)
                .isThrownBy(() -> service.uploadPhoto(pdf, Language.EN))
                .satisfies(thrown -> assertThat(thrown.getCode())
                        .isEqualTo(InvalidUploadException.TYPE_NOT_ALLOWED));

        verify(fileStorage, never()).store(any(), any());
        verify(imageSanitiser, never()).sanitise(any());
    }

    @Test
    @DisplayName("removing a photo deletes the file and makes the profile incomplete again")
    void removingAPhotoDeletesTheFile() {
        TutorProfile profile = profileWithPhoto();
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.of(profile));

        var dto = service.removePhoto(Language.EN);

        assertThat(dto.photoUrl()).isNull();
        assertThat(dto.missingFields()).contains("PHOTO");
        verify(fileStorage).delete(PHOTO_KEY);
    }

    @Test
    @DisplayName("the public read asks for a published profile, so a draft is simply not found")
    void publicReadFiltersOnStatusInTheQuery() {
        when(profiles.findByTutorIdAndStatus(TUTOR_ID, ProfileStatus.PUBLISHED)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.publicProfile(TUTOR_ID, Language.EN));

        verify(profiles).findByTutorIdAndStatus(TUTOR_ID, ProfileStatus.PUBLISHED);
        verify(profiles, never()).findByTutorId(any());
    }

    @Test
    @DisplayName("the public read carries the tutor's name and no way to contact them off-platform")
    void publicReadCarriesNoContactDetails() {
        activateTutor();
        when(profiles.findByTutorIdAndStatus(TUTOR_ID, ProfileStatus.PUBLISHED))
                .thenReturn(Optional.of(publishedProfile()));

        var dto = service.publicProfile(TUTOR_ID, Language.EN);

        assertThat(dto.fullName()).isEqualTo("Kasun Perera");
        // The proof is structural rather than a value check: TutorProfileDto has no field that
        // could hold either, so this asserts the record itself has not grown one.
        assertThat(dto.toString()).doesNotContain("kasun@example.lk", "+94771234567");
    }

    @Test
    @DisplayName("a published profile whose account is suspended is not public: search and the page agree")
    void aSuspendedAccountIsNotListed() {
        activateTutor();
        TutorProfile profile = publishedProfile();
        tutor.suspend();
        when(profiles.findByTutorIdAndStatus(TUTOR_ID, ProfileStatus.PUBLISHED)).thenReturn(Optional.of(profile));

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.publicProfile(TUTOR_ID, Language.EN));
    }

    private void activateTutor() {
        tutor.verifyEmail();
        tutor.verifyPhone();
        tutor.activate();
    }

    private TutorProfile existingProfile() {
        return new TutorProfile(tutor);
    }

    /** A profile whose photo has already been uploaded, which a draft save can no longer do. */
    private TutorProfile profileWithPhoto() {
        TutorProfile profile = existingProfile();
        profile.attachPhoto(PHOTO_URL);
        return profile;
    }

    private TutorProfile completeProfile() {
        TutorProfile profile = existingProfile();
        profile.describe("A/L Chemistry", "Fifteen years of A/L Chemistry.");
        profile.attachPhoto(PHOTO_URL);
        profile.teaches(Set.of(subject()), Set.of(examLevel()), Set.of(syllabus()), Set.of(Medium.ENGLISH));
        profile.delivers(Set.of(ClassFormat.SMALL_GROUP), true, AvailabilityStatus.ACCEPTING);
        profile.chargesBetween(new BigDecimal("1500"), new BigDecimal("2500"), FeeUnit.PER_MONTH);
        return profile;
    }

    private TutorProfile publishedProfile() {
        TutorProfile profile = completeProfile();
        profile.publish();
        return profile;
    }

    private static TutorProfileRequest completeRequest() {
        return new TutorProfileRequest(
                "A/L Chemistry in Nugegoda",
                "Fifteen years preparing students for A/L Chemistry.",
                Set.of("CHEMISTRY"),
                Set.of("GCE_AL"),
                Set.of("NATIONAL_ENGLISH"),
                Set.of("COLOMBO"),
                Set.of(Medium.ENGLISH),
                Set.of(ClassFormat.SMALL_GROUP),
                List.of(new QualificationRequest("BSc Chemistry", "University of Colombo", 2008)),
                15,
                new BigDecimal("1500.00"),
                new BigDecimal("2500.00"),
                FeeUnit.PER_MONTH,
                "COLOMBO",
                15,
                false,
                AvailabilityStatus.LIMITED);
    }

    private static TutorProfileRequest requestWithSubjects(Set<String> subjectCodes) {
        return new TutorProfileRequest(null, null,
                subjectCodes, null, null, null, null, null, null, null, null, null, null, null, null,
                false, AvailabilityStatus.ACCEPTING);
    }

    /** A minimal but genuine JPEG signature - what the detector actually looks at. */
    private static byte[] jpegBytes() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    }

    private static byte[] pdfBytes() {
        return "%PDF-1.4 not really a pdf".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static Subject subject() {
        return new Subject("CHEMISTRY", 1);
    }

    private static ExamLevel examLevel() {
        return new ExamLevel("GCE_AL", 1);
    }

    private static Syllabus syllabus() {
        return new Syllabus("NATIONAL_ENGLISH", 1);
    }

    private static Area area() {
        return Area.district("COLOMBO", 1, new BigDecimal("6.927079"), new BigDecimal("79.861244"));
    }
}
