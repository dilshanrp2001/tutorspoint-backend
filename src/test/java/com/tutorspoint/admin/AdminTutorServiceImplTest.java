package com.tutorspoint.admin;

import com.tutorspoint.admin.dto.AdminDocumentResponse;
import com.tutorspoint.admin.dto.AdminTutorDetail;
import com.tutorspoint.admin.event.DocumentReviewedEvent;
import com.tutorspoint.admin.event.ProfileModeratedEvent;
import com.tutorspoint.admin.event.TutorVerificationChangedEvent;
import com.tutorspoint.auth.domain.Admin;
import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.CurrentUser;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.common.exception.ResourceNotFoundException;
import com.tutorspoint.common.storage.FileContent;
import com.tutorspoint.common.storage.FileType;
import com.tutorspoint.common.storage.TestFiles;
import com.tutorspoint.common.storage.UploadedFile;
import com.tutorspoint.tutor.domain.ProfileField;
import com.tutorspoint.tutor.domain.ProfileStatus;
import com.tutorspoint.tutor.domain.TutorProfile;
import com.tutorspoint.tutor.repository.TutorProfileRepository;
import com.tutorspoint.verification.domain.DocumentStatus;
import com.tutorspoint.verification.domain.DocumentType;
import com.tutorspoint.verification.domain.VerificationDocument;
import com.tutorspoint.verification.repository.VerificationDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tutor review with every repository and the event bus mocked.
 *
 * <p>The rules under test are the service's own: a real change publishes exactly one audited
 * event, a repeated or refused one publishes none, and a rejection cannot go out without a
 * reason. What each transition is allowed to do is the entities' rule and has its own tests.
 */
@ExtendWith(MockitoExtension.class)
class AdminTutorServiceImplTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long TUTOR_ID = 7L;
    private static final Long PROFILE_ID = 70L;
    private static final Long DOCUMENT_ID = 100L;
    private static final Instant NOW = Instant.parse("2026-09-16T08:30:00Z");

    @Mock
    private UserRepository users;

    @Mock
    private TutorProfileRepository profiles;

    @Mock
    private VerificationDocumentRepository documents;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private ApplicationEventPublisher events;

    private AdminTutorServiceImpl service;

    private Tutor tutor;
    private Admin admin;

    @BeforeEach
    void setUp() {
        service = new AdminTutorServiceImpl(users, profiles, documents, new AdminMapperImpl(), currentUser, events,
                Clock.fixed(NOW, ZoneOffset.UTC));
        tutor = withId(new Tutor("kasun@example.lk", "hash", "Kasun Perera", "+94771234567", Language.EN), TUTOR_ID);
        admin = withId(new Admin("ops@tutorspoint.lk", "hash", "Ops Reviewer", "+94700000001", Language.EN), ADMIN_ID);
        lenient().when(currentUser.requireId()).thenReturn(ADMIN_ID);
        lenient().when(users.findById(TUTOR_ID)).thenReturn(Optional.of(tutor));
        lenient().when(users.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        lenient().when(documents.findWithReviewerByTutorIdOrderByCreatedAtDesc(TUTOR_ID)).thenReturn(List.of());
    }

    @Test
    @DisplayName("the review screen carries the account, the profile and the documents")
    void tutorDetail() {
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.of(profile()));
        when(documents.findWithReviewerByTutorIdOrderByCreatedAtDesc(TUTOR_ID)).thenReturn(List.of(document()));

        AdminTutorDetail detail = service.tutor(TUTOR_ID);

        assertThat(detail.account().fullName()).isEqualTo("Kasun Perera");
        assertThat(detail.profile().status()).isEqualTo(ProfileStatus.DRAFT);
        assertThat(detail.profile().missingFields()).contains(ProfileField.PHOTO);
        assertThat(detail.documents()).extracting(AdminDocumentResponse::status).containsExactly(DocumentStatus.PENDING);
    }

    @Test
    @DisplayName("a tutor who never opened the wizard has no profile, and that is not an error")
    void tutorWithoutAProfile() {
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.empty());

        assertThat(service.tutor(TUTOR_ID).profile()).isNull();
    }

    @Test
    @DisplayName("an id that is not a tutor's is not found, even when the account exists")
    void aParentIsNotATutor() {
        Parent parent = new Parent("niluka@example.lk", "hash", "Niluka", "+94771234568", Language.EN);
        when(users.findById(55L)).thenReturn(Optional.of(parent));

        assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> service.tutor(55L));
    }

    @Test
    @DisplayName("approving records the reviewer and the moment, and publishes the audited event")
    void approves() {
        VerificationDocument document = document();
        when(documents.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));

        AdminDocumentResponse result = service.approveDocument(DOCUMENT_ID, "Matches the NIC");

        assertThat(result.status()).isEqualTo(DocumentStatus.APPROVED);
        assertThat(result.reviewedByName()).isEqualTo("Ops Reviewer");
        assertThat(result.reviewedAt()).isEqualTo(NOW);
        verify(events).publishEvent(new DocumentReviewedEvent(ADMIN_ID, DOCUMENT_ID, TUTOR_ID,
                DocumentStatus.APPROVED, "Matches the NIC"));
    }

    @Test
    @DisplayName("rejecting records the reason the tutor will be shown")
    void rejects() {
        when(documents.findById(DOCUMENT_ID)).thenReturn(Optional.of(document()));

        AdminDocumentResponse result = service.rejectDocument(DOCUMENT_ID, "The scan is unreadable");

        assertThat(result.status()).isEqualTo(DocumentStatus.REJECTED);
        assertThat(result.reviewNotes()).isEqualTo("The scan is unreadable");
        verify(events).publishEvent(new DocumentReviewedEvent(ADMIN_ID, DOCUMENT_ID, TUTOR_ID,
                DocumentStatus.REJECTED, "The scan is unreadable"));
    }

    @Test
    @DisplayName("a document already ruled on cannot be ruled on again, and nothing is recorded")
    void aDecisionIsFinal() {
        VerificationDocument document = document();
        document.approve(admin, null, NOW);
        when(documents.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));

        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(() -> service.rejectDocument(DOCUMENT_ID, "changed my mind"));
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("granting the badge stamps it now and records the before and after")
    void verifies() {
        TutorProfile profile = profile();
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.of(profile));

        AdminTutorDetail detail = service.setVerified(TUTOR_ID, true);

        assertThat(detail.profile().verified()).isTrue();
        assertThat(detail.profile().verifiedAt()).isEqualTo(NOW);
        verify(events).publishEvent(new TutorVerificationChangedEvent(ADMIN_ID, TUTOR_ID, PROFILE_ID, true, null, NOW));
    }

    @Test
    @DisplayName("withdrawing the badge keeps when it had been granted on the record")
    void revokes() {
        TutorProfile profile = profile();
        Instant grantedAt = Instant.parse("2026-09-01T00:00:00Z");
        profile.verify(grantedAt);
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.of(profile));

        service.setVerified(TUTOR_ID, false);

        assertThat(profile.isVerified()).isFalse();
        verify(events).publishEvent(new TutorVerificationChangedEvent(ADMIN_ID, TUTOR_ID, PROFILE_ID, false, grantedAt, null));
    }

    @Test
    @DisplayName("asking for the badge state a tutor already has changes nothing - not even the date")
    void verifyingTwiceIsANoOp() {
        TutorProfile profile = profile();
        Instant grantedAt = Instant.parse("2026-09-01T00:00:00Z");
        profile.verify(grantedAt);
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.of(profile));

        service.setVerified(TUTOR_ID, true);

        assertThat(profile.getVerifiedAt()).isEqualTo(grantedAt);
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("a tutor with no profile has nothing to carry a badge")
    void noProfileNoBadge() {
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> service.setVerified(TUTOR_ID, true));
    }

    @Test
    @DisplayName("suspending a profile takes it down with the reason on record; twice is a no-op")
    void suspendsAProfile() {
        TutorProfile profile = profile();
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.of(profile));

        service.suspendProfile(TUTOR_ID, "Misleading qualifications");
        service.suspendProfile(TUTOR_ID, "Misleading qualifications");

        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.SUSPENDED);
        verify(events).publishEvent(new ProfileModeratedEvent(ADMIN_ID, TUTOR_ID, PROFILE_ID,
                ProfileStatus.DRAFT, ProfileStatus.SUSPENDED, "Misleading qualifications"));
    }

    @Test
    @DisplayName("reinstating hands the profile back as a draft, audited")
    void reinstatesAProfile() {
        TutorProfile profile = profile();
        profile.suspend();
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.of(profile));

        service.reinstateProfile(TUTOR_ID, "Corrected");

        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.DRAFT);
        verify(events).publishEvent(new ProfileModeratedEvent(ADMIN_ID, TUTOR_ID, PROFILE_ID,
                ProfileStatus.SUSPENDED, ProfileStatus.DRAFT, "Corrected"));
    }

    @Test
    @DisplayName("reinstating a profile that is not suspended is refused rather than silently ignored")
    void reinstatingAnUnsuspendedProfileIsRefused() {
        when(profiles.findByTutorId(TUTOR_ID)).thenReturn(Optional.of(profile()));

        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(() -> service.reinstateProfile(TUTOR_ID, null))
                .satisfies(e -> assertThat(e.getCode()).isEqualTo("PROFILE_NOT_SUSPENDED"));
        verify(events, never()).publishEvent(any());
    }

    private TutorProfile profile() {
        return withId(new TutorProfile(tutor), PROFILE_ID);
    }

    private VerificationDocument document() {
        return withId(new VerificationDocument(tutor, DocumentType.NIC, "documents/key.pdf",
                new UploadedFile("nic.pdf", TestFiles.pdf()), new FileContent(FileType.PDF, TestFiles.pdf())), DOCUMENT_ID);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
