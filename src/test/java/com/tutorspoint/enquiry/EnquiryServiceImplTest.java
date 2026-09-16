package com.tutorspoint.enquiry;

import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.Role;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.domain.User;
import com.tutorspoint.auth.repository.ChildProfileRepository;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.AuthenticatedUser;
import com.tutorspoint.auth.security.CurrentUser;
import com.tutorspoint.common.domain.ClassFormat;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.common.exception.ResourceNotFoundException;
import com.tutorspoint.enquiry.config.EnquiryProperties;
import com.tutorspoint.enquiry.domain.Enquiry;
import com.tutorspoint.enquiry.domain.EnquiryStatus;
import com.tutorspoint.enquiry.dto.ContactDetailsDto;
import com.tutorspoint.enquiry.dto.EnquiryMessageRequest;
import com.tutorspoint.enquiry.dto.EnquiryRequest;
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
import com.tutorspoint.tutor.domain.TutorProfile;
import com.tutorspoint.tutor.repository.TutorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * The enquiry service, tested for the four things it is responsible for and nothing the
 * entity already guarantees: who may send, how often, what gets scrubbed, and which events
 * come out the other side.
 *
 * <p>The event assertions are the Observer contract written down. This service must not call
 * {@code NotificationService} — there is no mock for one here, and adding one would be the
 * first sign that the dependency had crept back in.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnquiryServiceImplTest {

    private static final Long PARENT_ID = 1L;
    private static final Long TUTOR_ID = 2L;
    private static final Long ENQUIRY_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");

    private static final EnquiryRequest REQUEST = new EnquiryRequest(
            TUTOR_ID, null, "CHEMISTRY", "GCE_AL", ClassFormat.ONE_TO_ONE, "COLOMBO", false,
            "Do you teach on weekends? Call me on 0771234567");

    @Mock private EnquiryRepository enquiries;
    @Mock private UserRepository users;
    @Mock private ChildProfileRepository childProfiles;
    @Mock private TutorProfileRepository tutorProfiles;
    @Mock private SubjectRepository subjects;
    @Mock private ExamLevelRepository examLevels;
    @Mock private AreaRepository areas;
    @Mock private EnquiryMapper enquiryMapper;
    @Mock private ReferenceLabels labels;
    @Mock private ContactDetailScrubber scrubber;
    @Mock private CurrentUser currentUser;
    @Mock private ApplicationEventPublisher events;

    private final EnquiryProperties properties = new EnquiryProperties(10, "https://tutorspoint.lk/enquiries");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private Parent parent;
    private Tutor tutor;
    private EnquiryServiceImpl service;

    @BeforeEach
    void setUp() {
        parent = activeParent();
        tutor = activeTutor();
        service = new EnquiryServiceImpl(enquiries, users, childProfiles, tutorProfiles, subjects,
                examLevels, areas, enquiryMapper, labels, scrubber, currentUser, events, properties, clock);

        given(currentUser.requireId()).willReturn(PARENT_ID);
        given(currentUser.require()).willReturn(new AuthenticatedUser(PARENT_ID, parent.getEmail(), Role.PARENT));
        given(users.findById(PARENT_ID)).willReturn(Optional.of(parent));
        given(users.findById(TUTOR_ID)).willReturn(Optional.of(tutor));
        given(tutorProfiles.findByTutorIdAndStatus(TUTOR_ID, ProfileStatus.PUBLISHED))
                .willReturn(Optional.of(publishedProfile()));
        given(subjects.findByCodeInAndActiveTrue(List.of("CHEMISTRY"))).willReturn(List.of(subject()));
        given(examLevels.findByCodeInAndActiveTrue(List.of("GCE_AL"))).willReturn(List.of(examLevel()));
        given(areas.findByCodeAndActiveTrue("COLOMBO")).willReturn(Optional.of(area()));
        given(scrubber.scrub(any(), any())).willAnswer(call -> call.getArgument(0, String.class));
    }

    @Test
    @DisplayName("the first message is scrubbed before it is ever stored")
    void theOpeningMessageIsScrubbed() {
        given(scrubber.scrub(REQUEST.message(), Language.EN)).willReturn("Do you teach on weekends? [removed]");

        service.create(REQUEST, Language.EN);

        ArgumentCaptor<Enquiry> saved = ArgumentCaptor.forClass(Enquiry.class);
        then(enquiries).should().saveAndFlush(saved.capture());
        assertThat(saved.getValue().firstMessage()).get()
                .extracting("body")
                .isEqualTo("Do you teach on weekends? [removed]");
        // Scrubbed in the writer's own language, not the caller's Accept-Language.
        then(scrubber).should().scrub(REQUEST.message(), Language.EN);
    }

    @Test
    @DisplayName("creating publishes EnquiryCreatedEvent carrying what a listener needs")
    void creatingPublishesTheEvent() {
        service.create(REQUEST, Language.EN);

        ArgumentCaptor<EnquiryCreatedEvent> published = ArgumentCaptor.forClass(EnquiryCreatedEvent.class);
        then(events).should().publishEvent(published.capture());
        assertThat(published.getValue()).satisfies(event -> {
            assertThat(event.parentId()).isEqualTo(PARENT_ID);
            assertThat(event.tutorId()).isEqualTo(TUTOR_ID);
            assertThat(event.tutorEmail()).isEqualTo(tutor.getEmail());
            // The tutor reads Sinhala; the subject name is resolved for them, not for the caller.
            assertThat(event.tutorLanguage()).isEqualTo(Language.SI);
            assertThat(event.parentName()).isEqualTo(parent.getFullName());
        });
    }

    @Test
    @DisplayName("a suspended parent cannot send, whatever their token still says")
    void onlyActiveAccountsMaySend() {
        parent.suspend();

        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(() -> service.create(REQUEST, Language.EN))
                .withMessageContaining("cannot send enquiries");
        then(enquiries).should(never()).saveAndFlush(any());
        then(events).should(never()).publishEvent(any(EnquiryCreatedEvent.class));
    }

    @Test
    @DisplayName("a tutor with no published profile cannot be enquired to, and is simply not found")
    void unlistedTutorsAreNotFound() {
        given(tutorProfiles.findByTutorIdAndStatus(TUTOR_ID, ProfileStatus.PUBLISHED)).willReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.create(REQUEST, Language.EN));
    }

    @Test
    @DisplayName("a second live thread with the same tutor is refused")
    void oneLiveThreadPerTutor() {
        given(enquiries.existsByParentIdAndTutorIdAndStatusIn(anyLong(), anyLong(), anySet())).willReturn(true);

        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(() -> service.create(REQUEST, Language.EN))
                .withMessageContaining("already has an open enquiry");
        then(enquiries).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("the hourly cap is counted from an hour ago, and refuses the one over")
    void theHourlyCapIsEnforced() {
        given(enquiries.countByParentIdAndCreatedAtAfter(PARENT_ID, NOW.minusSeconds(3600)))
                .willReturn((long) properties.maxPerHour());

        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(() -> service.create(REQUEST, Language.EN))
                .withMessageContaining("in the last hour");
        then(events).should(never()).publishEvent(any(EnquiryCreatedEvent.class));
    }

    @Test
    @DisplayName("a thread the caller is not in is not found, rather than forbidden")
    void nonParticipantsGetNotFound() {
        given(enquiries.findByIdAndParentId(ENQUIRY_ID, PARENT_ID)).willReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.thread(ENQUIRY_ID, Language.EN));
    }

    @Test
    @DisplayName("an unanswered thread hands the mapper no contact details")
    void nothingIsRevealedBeforeTheTutorReplies() {
        Enquiry enquiry = existingThread();
        given(enquiries.findByIdAndParentId(ENQUIRY_ID, PARENT_ID)).willReturn(Optional.of(enquiry));

        service.thread(ENQUIRY_ID, Language.EN);

        then(enquiryMapper).should().toDetail(enquiry, null, Language.EN, labels);
        then(enquiryMapper).should(never()).toContact(any());
    }

    @Test
    @DisplayName("once the tutor has replied, the parent is given the tutor's details and nobody else's")
    void theParentSeesTheTutorsDetailsAfterTheReply() {
        Enquiry enquiry = existingThread();
        enquiry.reply(tutor, "Yes, weekends are free", NOW);
        given(enquiries.findByIdAndParentId(ENQUIRY_ID, PARENT_ID)).willReturn(Optional.of(enquiry));
        ContactDetailsDto tutorContact =
                new ContactDetailsDto(tutor.getFullName(), tutor.getEmail(), tutor.getPhoneNumber());
        given(enquiryMapper.toContact(tutor)).willReturn(tutorContact);

        service.thread(ENQUIRY_ID, Language.EN);

        then(enquiryMapper).should().toContact(tutor);
        then(enquiryMapper).should().toDetail(enquiry, tutorContact, Language.EN, labels);
    }

    @Test
    @DisplayName("the tutor's first reply publishes EnquiryRespondedEvent; later ones do not")
    void theFirstReplyPublishesTheRespondedEvent() {
        Enquiry enquiry = existingThread();
        given(currentUser.require()).willReturn(new AuthenticatedUser(TUTOR_ID, tutor.getEmail(), Role.TUTOR));
        given(enquiries.findByIdAndTutorId(ENQUIRY_ID, TUTOR_ID)).willReturn(Optional.of(enquiry));

        service.addMessage(ENQUIRY_ID, new EnquiryMessageRequest("Yes, weekends are free"), Language.EN);

        ArgumentCaptor<EnquiryRespondedEvent> published = ArgumentCaptor.forClass(EnquiryRespondedEvent.class);
        then(events).should().publishEvent(published.capture());
        assertThat(published.getValue().parentEmail()).isEqualTo(parent.getEmail());
        assertThat(published.getValue().parentLanguage()).isEqualTo(Language.EN);

        service.addMessage(ENQUIRY_ID, new EnquiryMessageRequest("Four o'clock?"), Language.EN);

        // Still exactly one: a platform that emails on every message is one people mute.
        then(events).should().publishEvent(any(EnquiryRespondedEvent.class));
        assertThat(enquiry.getStatus()).isEqualTo(EnquiryStatus.RESPONDED);
    }

    @Test
    @DisplayName("messages are scrubbed while masked and left alone once revealed")
    void scrubbingStopsWhenTheChannelOpens() {
        Enquiry enquiry = existingThread();
        given(currentUser.require()).willReturn(new AuthenticatedUser(PARENT_ID, parent.getEmail(), Role.PARENT));
        given(enquiries.findByIdAndParentId(ENQUIRY_ID, PARENT_ID)).willReturn(Optional.of(enquiry));

        service.addMessage(ENQUIRY_ID, new EnquiryMessageRequest("Ring 0771234567"), Language.EN);
        then(scrubber).should().scrub("Ring 0771234567", Language.EN);

        enquiry.reply(tutor, "Yes", NOW);
        service.addMessage(ENQUIRY_ID, new EnquiryMessageRequest("My number is 0771234567"), Language.EN);

        // Nothing to hide any more: both sides already have each other's details.
        then(scrubber).should(never()).scrub("My number is 0771234567", Language.EN);
        assertThat(enquiry.lastMessage()).get().extracting("body").isEqualTo("My number is 0771234567");
    }

    @Test
    @DisplayName("a tutor reading an unopened enquiry marks it VIEWED; a parent reading their own does not")
    void onlyTheTutorMarksAThreadViewed() {
        Enquiry parentRead = existingThread();
        given(enquiries.findByIdAndParentId(ENQUIRY_ID, PARENT_ID)).willReturn(Optional.of(parentRead));
        service.thread(ENQUIRY_ID, Language.EN);
        assertThat(parentRead.getStatus()).isEqualTo(EnquiryStatus.SENT);

        Enquiry tutorRead = existingThread();
        given(currentUser.require()).willReturn(new AuthenticatedUser(TUTOR_ID, tutor.getEmail(), Role.TUTOR));
        given(enquiries.findByIdAndTutorId(ENQUIRY_ID, TUTOR_ID)).willReturn(Optional.of(tutorRead));
        service.thread(ENQUIRY_ID, Language.EN);
        assertThat(tutorRead.getStatus()).isEqualTo(EnquiryStatus.VIEWED);
    }

    @Test
    @DisplayName("an administrator has no inbox and no way into a thread")
    void administratorsAreNotParticipants() {
        given(currentUser.require()).willReturn(new AuthenticatedUser(99L, "admin@tutorspoint.lk", Role.ADMIN));

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.thread(ENQUIRY_ID, Language.EN));
        assertThat(service.myEnquiries(null, 0, 20, Language.EN).enquiries()).isEmpty();
    }

    @Test
    @DisplayName("the unread count is asked of the caller's own side, and an administrator has none")
    void theUnreadCountIsTheCallersOwn() {
        given(enquiries.countUnreadForParent(PARENT_ID)).willReturn(3L);
        assertThat(service.unreadCount().unreadCount()).isEqualTo(3);

        given(currentUser.require()).willReturn(new AuthenticatedUser(TUTOR_ID, tutor.getEmail(), Role.TUTOR));
        given(enquiries.countUnreadForTutor(TUTOR_ID)).willReturn(5L);
        assertThat(service.unreadCount().unreadCount()).isEqualTo(5);

        given(currentUser.require()).willReturn(new AuthenticatedUser(99L, "admin@tutorspoint.lk", Role.ADMIN));
        assertThat(service.unreadCount().unreadCount()).isZero();
        then(enquiries).should(never()).countUnreadForParent(99L);
        then(enquiries).should(never()).countUnreadForTutor(99L);
    }

    private Enquiry existingThread() {
        Enquiry enquiry = Enquiry.open(parent, tutor, null, subject(), examLevel(),
                ClassFormat.ONE_TO_ONE, area(), false, "Do you teach on weekends?");
        ReflectionTestUtils.setField(enquiry, "id", ENQUIRY_ID);
        return enquiry;
    }

    private static Parent activeParent() {
        Parent parent = new Parent("niluka@example.lk", "hash", "Niluka", "+94770000001", Language.EN);
        activate(parent, PARENT_ID);
        return parent;
    }

    private static Tutor activeTutor() {
        Tutor tutor = new Tutor("kasun@example.lk", "hash", "Kasun", "+94770000002", Language.SI);
        activate(tutor, TUTOR_ID);
        return tutor;
    }

    private static void activate(User user, Long id) {
        ReflectionTestUtils.setField(user, "id", id);
        user.verifyEmail();
        user.verifyPhone();
        user.activate();
    }

    private TutorProfile publishedProfile() {
        return new TutorProfile(tutor);
    }

    private static Subject subject() {
        return new Subject("CHEMISTRY", 1);
    }

    private static ExamLevel examLevel() {
        return new ExamLevel("GCE_AL", 1);
    }

    private static Area area() {
        return Area.district("COLOMBO", 1, new BigDecimal("6.9271"), new BigDecimal("79.8612"));
    }
}
