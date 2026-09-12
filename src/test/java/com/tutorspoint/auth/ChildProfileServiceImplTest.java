package com.tutorspoint.auth;

import com.tutorspoint.auth.domain.ChildProfile;
import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.dto.ChildProfileRequest;
import com.tutorspoint.auth.repository.ChildProfileRepository;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.CurrentUser;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.ResourceNotFoundException;
import com.tutorspoint.common.exception.UnauthorizedActionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * Child sub-profiles (FR-A5), and the shape of their authorization: every read and write goes
 * through a finder that already carries the parent id, so another parent's child is not
 * "forbidden" — it is not found, which is also all a prober learns.
 */
@ExtendWith(MockitoExtension.class)
class ChildProfileServiceImplTest {

    private static final Long PARENT_ID = 7L;
    private static final Long OTHER_PARENTS_CHILD_ID = 99L;

    private static final ChildProfileRequest SANDUNI =
            new ChildProfileRequest("Sanduni", "Grade 11", "GCE O/L", "Visakha Vidyalaya", null);

    @Mock
    private UserRepository users;

    @Mock
    private ChildProfileRepository childProfiles;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private ChildProfileServiceImpl service;

    @Test
    void listingReadsOnlyTheCallersChildren() {
        given(currentUser.requireId()).willReturn(PARENT_ID);

        service.myChildren();

        then(childProfiles).should().findByParentIdOrderByNameAsc(PARENT_ID);
    }

    @Test
    void addingAttachesTheChildToTheCallersOwnAccount() {
        Parent parent = parent();
        givenCallerIs(parent);

        service.addChild(SANDUNI);

        assertThat(parent.getChildren())
                .extracting(ChildProfile::getName)
                .containsExactly("Sanduni");
        then(users).should().saveAndFlush(parent);
    }

    @Test
    void editingGoesThroughTheOwnerScopedFinder() {
        Parent parent = parent();
        ChildProfile child = parent.addChild("Sanduni", "Grade 10", "GCE O/L", null, null);
        ReflectionTestUtils.setField(child, "id", 12L);
        given(currentUser.requireId()).willReturn(PARENT_ID);
        given(childProfiles.findByIdAndParentId(12L, PARENT_ID)).willReturn(Optional.of(child));

        service.updateChild(12L, new ChildProfileRequest("Sanduni", "Grade 11", "GCE O/L", "Visakha", "Maths"));

        assertThat(child.getGrade()).isEqualTo("Grade 11");
        assertThat(child.getSchool()).isEqualTo("Visakha");
        assertThat(child.getNotes()).isEqualTo("Maths");
    }

    @Test
    void anotherParentsChildIsSimplyNotFound() {
        given(currentUser.requireId()).willReturn(PARENT_ID);
        given(childProfiles.findByIdAndParentId(OTHER_PARENTS_CHILD_ID, PARENT_ID)).willReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.updateChild(OTHER_PARENTS_CHILD_ID, SANDUNI));
    }

    @Test
    void removingGoesThroughTheParentSoTheRowIsActuallyDeleted() {
        Parent parent = parent();
        ChildProfile child = parent.addChild("Sanduni", "Grade 11", "GCE O/L", null, null);
        ReflectionTestUtils.setField(child, "id", 12L);
        givenCallerIs(parent);
        given(childProfiles.findByIdAndParentId(12L, PARENT_ID)).willReturn(Optional.of(child));

        service.removeChild(12L);

        assertThat(parent.getChildren()).isEmpty();
        then(users).should().save(parent);
    }

    @Test
    void anAccountThatIsNotAParentCannotHoldChildren() {
        Tutor tutor = new Tutor("kasun@example.lk", "$2a$10$hash", "Kasun", "+94772222222", Language.EN);
        given(currentUser.requireId()).willReturn(PARENT_ID);
        given(users.findById(PARENT_ID)).willReturn(Optional.of(tutor));

        assertThatExceptionOfType(UnauthorizedActionException.class)
                .isThrownBy(() -> service.addChild(SANDUNI));
    }

    private void givenCallerIs(Parent parent) {
        given(currentUser.requireId()).willReturn(PARENT_ID);
        given(users.findById(PARENT_ID)).willReturn(Optional.of(parent));
    }

    private static Parent parent() {
        Parent parent = new Parent("kamal@example.lk", "$2a$10$hash", "Kamal Silva", "+94773333333", Language.TA);
        ReflectionTestUtils.setField(parent, "id", PARENT_ID);
        parent.verifyEmail();
        parent.verifyPhone();
        parent.activate();
        return parent;
    }
}
