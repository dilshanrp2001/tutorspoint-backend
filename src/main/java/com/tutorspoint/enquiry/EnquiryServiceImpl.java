package com.tutorspoint.enquiry;

import com.tutorspoint.auth.domain.AccountStatus;
import com.tutorspoint.auth.domain.ChildProfile;
import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.Role;
import com.tutorspoint.auth.domain.Seeker;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.domain.User;
import com.tutorspoint.auth.repository.ChildProfileRepository;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.AuthenticatedUser;
import com.tutorspoint.auth.security.CurrentUser;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.common.exception.ResourceNotFoundException;
import com.tutorspoint.enquiry.config.EnquiryProperties;
import com.tutorspoint.enquiry.domain.Enquiry;
import com.tutorspoint.enquiry.domain.EnquiryStatus;
import com.tutorspoint.enquiry.dto.ContactDetailsDto;
import com.tutorspoint.enquiry.dto.EnquiryDetailResponse;
import com.tutorspoint.enquiry.dto.EnquiryListResponse;
import com.tutorspoint.enquiry.dto.EnquiryMessageRequest;
import com.tutorspoint.enquiry.dto.EnquiryRequest;
import com.tutorspoint.enquiry.dto.EnquirySummaryResponse;
import com.tutorspoint.enquiry.dto.EnquiryUnreadCountResponse;
import com.tutorspoint.enquiry.event.EnquiryCreatedEvent;
import com.tutorspoint.enquiry.event.EnquiryRespondedEvent;
import com.tutorspoint.enquiry.repository.EnquiryRepository;
import com.tutorspoint.enquiry.text.ContactDetailScrubber;
import com.tutorspoint.reference.ReferenceLabels;
import com.tutorspoint.reference.domain.Area;
import com.tutorspoint.reference.domain.ExamLevel;
import com.tutorspoint.reference.domain.Subject;
import com.tutorspoint.reference.repository.AreaRepository;
import com.tutorspoint.reference.repository.ExamLevelRepository;
import com.tutorspoint.reference.repository.SubjectRepository;
import com.tutorspoint.tutor.domain.ProfileStatus;
import com.tutorspoint.tutor.repository.TutorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Enquiry threads: opening one, reading one, answering one, ending one.
 *
 * <p>Three rules are worth knowing before reading the code.
 *
 * <p><strong>The caller is never a parameter.</strong> Every lookup goes through a finder
 * that takes the caller's id, so a thread belonging to somebody else is not found rather than
 * found-and-refused. There is no ownership comparison in this class to forget, and no branch
 * where a mistake would return a stranger's conversation.
 *
 * <p><strong>Masking is asked, never decided.</strong> {@link Enquiry#contactRevealed()} is the
 * rule; this class calls it, logs the reveal when it is true, and hands the mapper either the
 * counterpart's details or null. Nothing here can open the channel early, because the thing
 * that opens it is the tutor replying, stamped inside the entity.
 *
 * <p><strong>Notifications are not this class's business.</strong> It publishes
 * {@code EnquiryCreatedEvent} and {@code EnquiryRespondedEvent} and returns. The listeners are
 * bound to {@code AFTER_COMMIT}, which is what makes "persisted before any notification fires"
 * (FR-E3) a structural guarantee instead of a rule somebody has to remember.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnquiryServiceImpl implements EnquiryService {

    /** Stable keys the client switches on and translates into si / ta / en. */
    private static final String ERROR_ACCOUNT_NOT_ACTIVE = "ACCOUNT_NOT_ACTIVE";
    private static final String ERROR_ALREADY_OPEN = "ENQUIRY_ALREADY_OPEN";
    private static final String ERROR_RATE_LIMITED = "ENQUIRY_RATE_LIMITED";
    private static final String ERROR_CHILD_NOT_ALLOWED = "CHILD_PROFILE_NOT_ALLOWED";

    /** The statuses that mean "this conversation is still live". */
    private static final Set<EnquiryStatus> LIVE_STATUSES =
            Set.of(EnquiryStatus.SENT, EnquiryStatus.VIEWED, EnquiryStatus.RESPONDED);

    /** A page of an inbox. Bounded so a client cannot ask for every thread it has ever had. */
    private static final int MAX_PAGE_SIZE = 50;

    private final EnquiryRepository enquiries;
    private final UserRepository users;
    private final ChildProfileRepository childProfiles;
    private final TutorProfileRepository tutorProfiles;
    private final SubjectRepository subjects;
    private final ExamLevelRepository examLevels;
    private final AreaRepository areas;
    private final EnquiryMapper enquiryMapper;
    private final ReferenceLabels labels;
    private final ContactDetailScrubber scrubber;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;
    private final EnquiryProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('PARENT', 'STUDENT')")
    public EnquiryDetailResponse create(EnquiryRequest request, Language language) {
        Seeker seeker = requireActiveSeeker();
        Tutor tutor = requireListedTutor(request.tutorId());
        ensureWithinSendingLimits(seeker.getId(), tutor.getId());

        ChildProfile child = resolveChild(request.childProfileId(), seeker);
        Subject subject = resolveSubject(request.subjectCode());
        ExamLevel examLevel = resolveExamLevel(request.examLevelCode());
        Area preferredArea = resolveArea(request.preferredAreaCode());

        // Scrubbed before the entity ever sees it, so there is no window in which an
        // unscrubbed body exists in a field somebody might persist by mistake. The seeker's
        // own language, because the notice replaces words they wrote.
        String body = scrubber.scrub(request.message(), seeker.getPreferredLanguage());

        Enquiry enquiry = Enquiry.open(seeker, tutor, child, subject, examLevel,
                request.preferredFormat(), preferredArea, request.online(), body);
        // Flushed so the id is in the event and in the response, rather than a null the
        // notification would have to build a link without.
        enquiries.saveAndFlush(enquiry);

        log.info("{} {} opened enquiry {} with tutor {}", seeker.getRole(), seeker.getId(), enquiry.getId(), tutor.getId());
        events.publishEvent(new EnquiryCreatedEvent(
                enquiry.getId(),
                seeker.getId(),
                seeker.getFullName(),
                tutor.getId(),
                tutor.getEmail(),
                tutor.getFullName(),
                tutor.getPreferredLanguage(),
                // Rendered in the tutor's language here, where the entity is loaded, rather
                // than left as an id for a listener to resolve after the session has gone.
                subject.nameIn(tutor.getPreferredLanguage())));

        return enquiryMapper.toDetail(enquiry, null, language, labels);
    }

    @Override
    @Transactional(readOnly = true)
    public EnquiryListResponse myEnquiries(EnquiryStatus status, int page, int size, Language language) {
        AuthenticatedUser caller = currentUser.require();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        Page<Enquiry> found = pageFor(caller, status, pageable);

        List<EnquirySummaryResponse> rows = found.getContent().stream()
                .map(enquiry -> enquiryMapper.toSummary(enquiry, caller.userId(), language, labels))
                .toList();
        return new EnquiryListResponse(rows, found.getTotalElements(),
                pageable.getPageNumber(), pageable.getPageSize());
    }

    @Override
    @Transactional(readOnly = true)
    public EnquiryUnreadCountResponse unreadCount() {
        AuthenticatedUser caller = currentUser.require();
        long unread = switch (caller.role()) {
            case PARENT, STUDENT -> enquiries.countUnreadForSeeker(caller.userId());
            case TUTOR -> enquiries.countUnreadForTutor(caller.userId());
            case ADMIN -> 0;
        };
        return new EnquiryUnreadCountResponse(unread);
    }

    @Override
    @Transactional
    public EnquiryDetailResponse thread(Long enquiryId, Language language) {
        AuthenticatedUser caller = currentUser.require();
        Enquiry enquiry = requireOwnThread(caller, enquiryId);

        // Opening the enquiry is what "viewed" means (FR-E1), and only the tutor can do it:
        // a seeker re-reading their own question has not been answered by anybody.
        if (caller.role() == Role.TUTOR) {
            enquiry.markViewed();
        }
        enquiry.markMessagesRead(caller.userId(), clock.instant());

        return enquiryMapper.toDetail(enquiry, contactFor(enquiry, caller), language, labels);
    }

    @Override
    @Transactional
    public EnquiryDetailResponse addMessage(Long enquiryId, EnquiryMessageRequest request, Language language) {
        AuthenticatedUser caller = currentUser.require();
        Enquiry enquiry = requireOwnThread(caller, enquiryId);
        User sender = requireUser(caller.userId());

        boolean wasMasked = !enquiry.contactRevealed();
        // Scrubbed for as long as the thread is masked, not only on the first message. A
        // channel that closes on message one and opens on message two was never closed.
        String body = wasMasked
                ? scrubber.scrub(request.body(), sender.getPreferredLanguage())
                : request.body();

        enquiry.reply(sender, body, clock.instant());

        // The tutor's first reply, and only that, opens the channel and is worth an email.
        // Asking the entity whether the reveal just happened keeps the condition in one
        // place; a "is this the tutor and is this their first message" test written out here
        // would be a second copy of a rule that already exists.
        if (wasMasked && enquiry.contactRevealed()) {
            enquiries.flush();
            log.info("Tutor {} responded to enquiry {}; contact details are now revealed to both participants",
                    enquiry.getTutor().getId(), enquiry.getId());
            Seeker seeker = enquiry.getSeeker();
            events.publishEvent(new EnquiryRespondedEvent(
                    enquiry.getId(),
                    seeker.getId(),
                    seeker.getEmail(),
                    seeker.getFullName(),
                    seeker.getPreferredLanguage(),
                    enquiry.getTutor().getId(),
                    enquiry.getTutor().getFullName()));
        }

        return enquiryMapper.toDetail(enquiry, contactFor(enquiry, caller), language, labels);
    }

    @Override
    @Transactional
    public EnquiryDetailResponse close(Long enquiryId, Language language) {
        AuthenticatedUser caller = currentUser.require();
        Enquiry enquiry = requireOwnThread(caller, enquiryId);
        enquiry.close(requireUser(caller.userId()));
        log.info("Account {} closed enquiry {}", caller.userId(), enquiry.getId());
        return enquiryMapper.toDetail(enquiry, contactFor(enquiry, caller), language, labels);
    }

    /**
     * The other participant's details, once the thread has earned them — and a log line every
     * time they are handed over.
     *
     * <p>The reveal itself — the moment the channel opened — is on the durable audit record,
     * written from {@code EnquiryRespondedEvent} (NFR-10). This line records each later read of
     * details that were already revealed, which is operational detail rather than a new fact.
     */
    private ContactDetailsDto contactFor(Enquiry enquiry, AuthenticatedUser caller) {
        if (!enquiry.contactRevealed()) {
            return null;
        }
        User counterpart = enquiry.counterpartOf(caller.userId());
        log.info("Revealed contact details of account {} to account {} on enquiry {}, responded at {}",
                counterpart.getId(), caller.userId(), enquiry.getId(), enquiry.getFirstResponseAt());
        return enquiryMapper.toContact(counterpart);
    }

    /**
     * The caller's own thread, or nothing.
     *
     * <p>An administrator is deliberately not a participant. Moderation reads enquiries through
     * the admin endpoints in Phase 5, where the access is audited; this route answers the two
     * people in the conversation and nobody else.
     */
    private Enquiry requireOwnThread(AuthenticatedUser caller, Long enquiryId) {
        Optional<Enquiry> found = switch (caller.role()) {
            case PARENT, STUDENT -> enquiries.findByIdAndSeekerId(enquiryId, caller.userId());
            case TUTOR -> enquiries.findByIdAndTutorId(enquiryId, caller.userId());
            case ADMIN -> Optional.empty();
        };
        return found.orElseThrow(() -> new ResourceNotFoundException("Enquiry", enquiryId));
    }

    private Page<Enquiry> pageFor(AuthenticatedUser caller, EnquiryStatus status, Pageable pageable) {
        Long callerId = caller.userId();
        return switch (caller.role()) {
            case PARENT, STUDENT -> status == null
                    ? enquiries.findBySeekerIdOrderByCreatedAtDesc(callerId, pageable)
                    : enquiries.findBySeekerIdAndStatusOrderByCreatedAtDesc(callerId, status, pageable);
            case TUTOR -> status == null
                    ? enquiries.findByTutorIdOrderByCreatedAtDesc(callerId, pageable)
                    : enquiries.findByTutorIdAndStatusOrderByCreatedAtDesc(callerId, status, pageable);
            // An admin has no inbox of their own. An empty page rather than an error: the
            // endpoint is honest that there is nothing here for them.
            case ADMIN -> Page.empty(pageable);
        };
    }

    /**
     * The caller as an active seeker — a parent or a student.
     *
     * <p>The status is re-checked rather than trusted from the token: access tokens live for
     * fifteen minutes, and an account suspended for spamming tutors must stop being able to
     * spam tutors now, not when its token happens to expire.
     */
    private Seeker requireActiveSeeker() {
        Long callerId = currentUser.requireId();
        Seeker seeker = users.findById(callerId)
                .filter(Seeker.class::isInstance)
                .map(Seeker.class::cast)
                .orElseThrow(() -> new ResourceNotFoundException("Seeker account", callerId));
        if (seeker.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleViolationException(ERROR_ACCOUNT_NOT_ACTIVE,
                    "Account %s is %s and cannot send enquiries".formatted(callerId, seeker.getStatus()));
        }
        return seeker;
    }

    /**
     * The tutor being asked, if they are actually on offer.
     *
     * <p>A published profile is the condition, not merely an existing account: a seeker can
     * only have arrived here from a listing, and an enquiry to a tutor who has taken their
     * profile down would be a message nobody is expecting. The same "not found" covers a
     * suspended account, so the endpoint reveals nothing about why.
     */
    private Tutor requireListedTutor(Long tutorId) {
        Tutor tutor = users.findById(tutorId)
                .filter(Tutor.class::isInstance)
                .map(Tutor.class::cast)
                .filter(candidate -> candidate.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Tutor", tutorId));
        if (tutorProfiles.findByTutorIdAndStatus(tutorId, ProfileStatus.PUBLISHED).isEmpty()) {
            throw new ResourceNotFoundException("Tutor", tutorId);
        }
        return tutor;
    }

    /**
     * The two caps that stop one account flooding tutors (FR-E1).
     *
     * <p>They answer different questions. The first is about this pair: a second enquiry to a
     * tutor who has not answered the first is a nudge, and it belongs in the thread that
     * already exists. The second is about the hour: an account working through a district one
     * tutor at a time is stopped regardless of how polite each individual message is.
     */
    private void ensureWithinSendingLimits(Long seekerId, Long tutorId) {
        if (enquiries.existsBySeekerIdAndTutorIdAndStatusIn(seekerId, tutorId, LIVE_STATUSES)) {
            throw new BusinessRuleViolationException(ERROR_ALREADY_OPEN,
                    "Account %s already has an open enquiry with tutor %s".formatted(seekerId, tutorId));
        }
        Instant since = clock.instant().minus(Duration.ofHours(1));
        long recent = enquiries.countBySeekerIdAndCreatedAtAfter(seekerId, since);
        if (recent >= properties.maxPerHour()) {
            log.warn("Account {} hit the enquiry rate limit with {} in the last hour", seekerId, recent);
            throw new BusinessRuleViolationException(ERROR_RATE_LIMITED,
                    "Account %s has sent %s enquiries in the last hour".formatted(seekerId, recent));
        }
    }

    /**
     * Scoped to the parent, so another parent's child id is simply not found.
     *
     * <p>A student naming a child is refused before the look-up (FR-A8): "not found" would
     * suggest a child that might exist somewhere, when the truth is that a student has none.
     */
    private ChildProfile resolveChild(Long childProfileId, Seeker seeker) {
        if (childProfileId == null) {
            return null;
        }
        if (!(seeker instanceof Parent)) {
            throw new BusinessRuleViolationException(ERROR_CHILD_NOT_ALLOWED,
                    "Account %s is not a parent account and cannot name a child profile".formatted(seeker.getId()));
        }
        return childProfiles.findByIdAndParentId(childProfileId, seeker.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Child profile", childProfileId));
    }

    private Subject resolveSubject(String code) {
        return subjects.findByCodeInAndActiveTrue(List.of(code)).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Subject", code));
    }

    private ExamLevel resolveExamLevel(String code) {
        return examLevels.findByCodeInAndActiveTrue(List.of(code)).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Exam level", code));
    }

    /** Null when the seeker asked for online classes; {@code Enquiry.open} enforces the pairing. */
    private Area resolveArea(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return areas.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new ResourceNotFoundException("Area", code));
    }

    private User requireUser(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", userId));
    }
}
