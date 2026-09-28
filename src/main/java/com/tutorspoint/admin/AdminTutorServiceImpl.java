package com.tutorspoint.admin;

import com.tutorspoint.admin.dto.AdminDocumentResponse;
import com.tutorspoint.admin.dto.AdminTutorDetail;
import com.tutorspoint.admin.event.DocumentReviewedEvent;
import com.tutorspoint.admin.event.ProfileModeratedEvent;
import com.tutorspoint.admin.event.TutorVerificationChangedEvent;
import com.tutorspoint.auth.domain.Admin;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.CurrentUser;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.common.exception.ResourceNotFoundException;
import com.tutorspoint.common.exception.UnauthorizedActionException;
import com.tutorspoint.tutor.domain.ProfileStatus;
import com.tutorspoint.tutor.domain.TutorProfile;
import com.tutorspoint.tutor.repository.TutorProfileRepository;
import com.tutorspoint.verification.domain.VerificationDocument;
import com.tutorspoint.verification.repository.VerificationDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Tutor review and profile moderation.
 *
 * <p>The decisions themselves are the entities' - {@link VerificationDocument#approve},
 * {@link TutorProfile#verify}, {@link TutorProfile#suspend} - and each refuses what its domain
 * forbids. This class finds the rows, calls the method, and publishes the event that puts the
 * change on the audit record inside the same transaction.
 *
 * <p>A change that changes nothing publishes nothing. Verifying a verified tutor or suspending a
 * suspended profile is a double-click, not a second decision, and an audit trail padded with
 * no-ops is harder to read for the ones that matter.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminTutorServiceImpl implements AdminTutorService {

    private static final String ERROR_PROFILE_NOT_SUSPENDED = "PROFILE_NOT_SUSPENDED";

    private final UserRepository users;
    private final TutorProfileRepository profiles;
    private final VerificationDocumentRepository documents;
    private final AdminMapper adminMapper;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public AdminTutorDetail tutor(Long tutorId) {
        Tutor tutor = requireTutor(tutorId);
        TutorProfile profile = profiles.findByTutorId(tutorId).orElse(null);
        return new AdminTutorDetail(
                adminMapper.toSummary(tutor),
                profile == null ? null : adminMapper.toProfileView(profile),
                adminMapper.toDocuments(documents.findWithReviewerByTutorIdOrderByCreatedAtDesc(tutorId)));
    }

    @Override
    @Transactional
    public AdminDocumentResponse approveDocument(Long documentId, String notes) {
        VerificationDocument document = requireDocument(documentId);
        Admin reviewer = requireAdmin();
        document.approve(reviewer, notes, clock.instant());
        publishReview(reviewer, document);
        return adminMapper.toDocument(document);
    }

    @Override
    @Transactional
    public AdminDocumentResponse rejectDocument(Long documentId, String notes) {
        VerificationDocument document = requireDocument(documentId);
        Admin reviewer = requireAdmin();
        document.reject(reviewer, notes, clock.instant());
        publishReview(reviewer, document);
        return adminMapper.toDocument(document);
    }

    @Override
    @Transactional
    public AdminTutorDetail setVerified(Long tutorId, boolean verified) {
        TutorProfile profile = requireProfile(tutorId);
        if (profile.isVerified() != verified) {
            Instant previousVerifiedAt = profile.getVerifiedAt();
            if (verified) {
                profile.verify(clock.instant());
            } else {
                profile.revokeVerification();
            }
            events.publishEvent(new TutorVerificationChangedEvent(currentUser.requireId(), tutorId,
                    profile.getId(), verified, previousVerifiedAt, profile.getVerifiedAt()));
            log.info("Tutor {} verified badge set to {}", tutorId, verified);
        }
        return tutor(tutorId);
    }

    @Override
    @Transactional
    public AdminTutorDetail suspendProfile(Long tutorId, String reason) {
        TutorProfile profile = requireProfile(tutorId);
        if (profile.getStatus() != ProfileStatus.SUSPENDED) {
            ProfileStatus before = profile.getStatus();
            profile.suspend();
            events.publishEvent(new ProfileModeratedEvent(currentUser.requireId(), tutorId, profile.getId(),
                    before, profile.getStatus(), reason));
            log.info("Tutor {} profile suspended", tutorId);
        }
        return tutor(tutorId);
    }

    @Override
    @Transactional
    public AdminTutorDetail reinstateProfile(Long tutorId, String reason) {
        TutorProfile profile = requireProfile(tutorId);
        // TutorProfile.reinstate() quietly ignores a profile that is not suspended, which suits
        // the entity; from a moderator it is a stale screen, and saying so beats a silent no-op.
        if (profile.getStatus() != ProfileStatus.SUSPENDED) {
            throw new BusinessRuleViolationException(ERROR_PROFILE_NOT_SUSPENDED,
                    "Profile of tutor %s is %s, not suspended".formatted(tutorId, profile.getStatus()));
        }
        profile.reinstate();
        events.publishEvent(new ProfileModeratedEvent(currentUser.requireId(), tutorId, profile.getId(),
                ProfileStatus.SUSPENDED, profile.getStatus(), reason));
        log.info("Tutor {} profile reinstated as {}", tutorId, profile.getStatus());
        return tutor(tutorId);
    }

    private void publishReview(Admin reviewer, VerificationDocument document) {
        events.publishEvent(new DocumentReviewedEvent(reviewer.getId(), document.getId(),
                document.getTutor().getId(), document.getStatus(), document.getReviewNotes()));
        log.info("Document {} {}", document.getId(), document.getStatus());
    }

    private Tutor requireTutor(Long tutorId) {
        return users.findById(tutorId)
                .filter(Tutor.class::isInstance)
                .map(Tutor.class::cast)
                .orElseThrow(() -> new ResourceNotFoundException("Tutor", tutorId));
    }

    private TutorProfile requireProfile(Long tutorId) {
        return profiles.findByTutorId(tutorId)
                .orElseThrow(() -> new ResourceNotFoundException("Tutor profile", tutorId));
    }

    private VerificationDocument requireDocument(Long documentId) {
        return documents.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    }

    /** The reviewer is stored on the document as an account, so the row is loaded, not just the id. */
    private Admin requireAdmin() {
        Long callerId = currentUser.requireId();
        return users.findById(callerId)
                .filter(Admin.class::isInstance)
                .map(Admin.class::cast)
                // Unreachable through the HTTP routes, which only an admin token passes.
                .orElseThrow(() -> new UnauthorizedActionException(
                        "Account %s is not an administrator account".formatted(callerId)));
    }
}
