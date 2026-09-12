package com.tutorspoint.tutor;

import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.CurrentUser;
import com.tutorspoint.common.domain.ClassFormat;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.domain.Medium;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.common.exception.ResourceNotFoundException;
import com.tutorspoint.common.exception.UnauthorizedActionException;
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
                tutorMapper, referenceLabels, currentUser);
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
        TutorProfile profile = existingProfile();
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
        when(profiles.findByTutorIdAndStatus(TUTOR_ID, ProfileStatus.PUBLISHED))
                .thenReturn(Optional.of(publishedProfile()));

        var dto = service.publicProfile(TUTOR_ID, Language.EN);

        assertThat(dto.fullName()).isEqualTo("Kasun Perera");
        // The proof is structural rather than a value check: TutorProfileDto has no field that
        // could hold either, so this asserts the record itself has not grown one.
        assertThat(dto.toString()).doesNotContain("kasun@example.lk", "+94771234567");
    }

    private TutorProfile existingProfile() {
        return new TutorProfile(tutor);
    }

    private TutorProfile completeProfile() {
        TutorProfile profile = existingProfile();
        profile.describe("A/L Chemistry", "Fifteen years of A/L Chemistry.", "https://cdn.example.lk/p.jpg", null);
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
                "https://cdn.example.lk/photos/kasun.jpg",
                null,
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
        return new TutorProfileRequest(null, null, null, null,
                subjectCodes, null, null, null, null, null, null, null, null, null, null, null, null,
                false, AvailabilityStatus.ACCEPTING);
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
