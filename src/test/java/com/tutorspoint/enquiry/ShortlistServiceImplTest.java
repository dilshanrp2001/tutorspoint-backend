package com.tutorspoint.enquiry;

import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.domain.User;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.CurrentUser;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.ResourceNotFoundException;
import com.tutorspoint.enquiry.domain.Shortlist;
import com.tutorspoint.enquiry.dto.ShortlistRequest;
import com.tutorspoint.enquiry.repository.ShortlistRepository;
import com.tutorspoint.reference.ReferenceLabels;
import com.tutorspoint.tutor.domain.ProfileStatus;
import com.tutorspoint.tutor.domain.TutorProfile;
import com.tutorspoint.tutor.repository.TutorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Shortlists: owner scoping, the upsert rule, and the one query that keeps a list of saved
 * tutors from becoming a query per row.
 */
@ExtendWith(MockitoExtension.class)
class ShortlistServiceImplTest {

    private static final Long PARENT_ID = 1L;
    private static final Long TUTOR_ID = 2L;

    @Mock private ShortlistRepository shortlists;
    @Mock private UserRepository users;
    @Mock private TutorProfileRepository tutorProfiles;
    @Mock private EnquiryMapper enquiryMapper;
    @Mock private ReferenceLabels labels;
    @Mock private CurrentUser currentUser;

    @InjectMocks private ShortlistServiceImpl service;

    private Parent parent;
    private Tutor tutor;

    @BeforeEach
    void setUp() {
        parent = activate(new Parent("niluka@example.lk", "hash", "Niluka", "+94770000001", Language.EN), PARENT_ID);
        tutor = activate(new Tutor("kasun@example.lk", "hash", "Kasun", "+94770000002", Language.SI), TUTOR_ID);
    }

    @Test
    @DisplayName("the list is read only for the caller, and needs one query for every card")
    void listingIsOwnerScopedAndNotAnNPlusOne() {
        Shortlist entry = new Shortlist(parent, tutor, "Cheaper, but further away");
        given(currentUser.requireId()).willReturn(PARENT_ID);
        given(shortlists.findByParentIdOrderByCreatedAtDesc(PARENT_ID)).willReturn(List.of(entry));
        given(tutorProfiles.findByTutorIdInAndStatus(List.of(TUTOR_ID), ProfileStatus.PUBLISHED))
                .willReturn(List.of(profile()));

        service.myShortlist(Language.EN);

        then(tutorProfiles).should().findByTutorIdInAndStatus(List.of(TUTOR_ID), ProfileStatus.PUBLISHED);
        then(tutorProfiles).should(never()).findByTutorIdAndStatus(any(), any());
    }

    @Test
    @DisplayName("an empty shortlist asks the profile table nothing at all")
    void anEmptyListQueriesNothingFurther() {
        given(currentUser.requireId()).willReturn(PARENT_ID);
        given(shortlists.findByParentIdOrderByCreatedAtDesc(PARENT_ID)).willReturn(List.of());

        assertThat(service.myShortlist(Language.EN)).isEmpty();
        then(tutorProfiles).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("a tutor who has unpublished still appears, with no card")
    void anUnpublishedTutorKeepsItsRow() {
        Shortlist entry = new Shortlist(parent, tutor, null);
        given(currentUser.requireId()).willReturn(PARENT_ID);
        given(shortlists.findByParentIdOrderByCreatedAtDesc(PARENT_ID)).willReturn(List.of(entry));
        given(tutorProfiles.findByTutorIdInAndStatus(List.of(TUTOR_ID), ProfileStatus.PUBLISHED))
                .willReturn(List.of());

        service.myShortlist(Language.EN);

        then(enquiryMapper).should().toShortlistResponse(entry, null, Language.EN, labels);
    }

    @Test
    @DisplayName("saving the same tutor twice rewrites the note instead of adding a row")
    void savingTwiceIsAnUpsert() {
        Shortlist existing = new Shortlist(parent, tutor, "First thoughts");
        given(currentUser.requireId()).willReturn(PARENT_ID);
        given(tutorProfiles.findByTutorIdAndStatus(TUTOR_ID, ProfileStatus.PUBLISHED))
                .willReturn(Optional.of(profile()));
        given(shortlists.findByParentIdAndTutorId(PARENT_ID, TUTOR_ID)).willReturn(Optional.of(existing));

        service.save(TUTOR_ID, new ShortlistRequest("Second thoughts"), Language.EN);

        assertThat(existing.getNote()).isEqualTo("Second thoughts");
        then(shortlists).should(never()).save(any());
    }

    @Test
    @DisplayName("a tutor with no published profile cannot be shortlisted")
    void onlyListedTutorsCanBeSaved() {
        given(currentUser.requireId()).willReturn(PARENT_ID);
        given(tutorProfiles.findByTutorIdAndStatus(TUTOR_ID, ProfileStatus.PUBLISHED)).willReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.save(TUTOR_ID, new ShortlistRequest(null), Language.EN));
    }

    @Test
    @DisplayName("removing goes through the owner-scoped delete, so nobody unsaves another parent's tutor")
    void removingIsOwnerScoped() {
        given(currentUser.requireId()).willReturn(PARENT_ID);

        service.remove(TUTOR_ID);

        then(shortlists).should().deleteByParentIdAndTutorId(PARENT_ID, TUTOR_ID);
    }

    private TutorProfile profile() {
        TutorProfile profile = new TutorProfile(tutor);
        ReflectionTestUtils.setField(profile, "status", ProfileStatus.PUBLISHED);
        return profile;
    }

    private static <T extends User> T activate(T user, Long id) {
        ReflectionTestUtils.setField(user, "id", id);
        user.verifyEmail();
        user.verifyPhone();
        user.activate();
        return user;
    }
}
