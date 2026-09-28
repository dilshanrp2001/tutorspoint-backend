package com.tutorspoint.auth;

import com.tutorspoint.auth.domain.AccountStatus;
import com.tutorspoint.auth.domain.Role;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.dto.UpdateAccountRequest;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.auth.security.AuthenticatedUser;
import com.tutorspoint.auth.security.CurrentUser;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.ResourceNotFoundException;
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
 * Self-service account management.
 *
 * <p>What these tests are really checking is that the subject of every operation is the caller
 * in the token and nothing else: the id comes from {@link CurrentUser}, never from an argument.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    private static final Long CALLER_ID = 7L;

    @Mock
    private UserRepository users;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private AccountServiceImpl service;

    @Test
    void readingMyAccountLoadsTheRowTheTokenNames() {
        Tutor caller = activeTutor();
        givenCallerIs(caller);

        service.myAccount();

        then(users).should().findById(CALLER_ID);
        then(accountMapper).should().toAccountResponse(caller);
    }

    @Test
    void editingChangesTheNameAndThePreferredLanguage() {
        Tutor caller = activeTutor();
        givenCallerIs(caller);

        service.updateMyAccount(new UpdateAccountRequest("Nimali K. Perera", Language.TA));

        assertThat(caller.getFullName()).isEqualTo("Nimali K. Perera");
        assertThat(caller.getPreferredLanguage()).isEqualTo(Language.TA);
        // Untouched: both are verified identifiers, and changing either is its own flow.
        assertThat(caller.getEmail()).isEqualTo("nimali@example.lk");
        assertThat(caller.getPhoneNumber()).isEqualTo("+94771234567");
    }

    @Test
    void deletingIsASoftDeleteThatAlsoEndsEverySession() {
        Tutor caller = activeTutor();
        givenCallerIs(caller);

        service.deleteMyAccount();

        assertThat(caller.getStatus()).isEqualTo(AccountStatus.DELETED);
        then(refreshTokenService).should().revokeAllFor(CALLER_ID);
    }

    @Test
    void aTokenThatOutlivedItsAccountIsA404RatherThanACrash() {
        given(currentUser.requireId()).willReturn(CALLER_ID);
        given(users.findById(CALLER_ID)).willReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> service.myAccount());
    }

    @Test
    void theCallerIsNeverTakenFromAnArgument() {
        // The principal carries somebody else's id nowhere: there is no parameter to carry it.
        AuthenticatedUser principal = new AuthenticatedUser(CALLER_ID, "nimali@example.lk", Role.TUTOR);

        assertThat(principal.owns(CALLER_ID)).isTrue();
        assertThat(principal.owns(8L)).isFalse();
        assertThat(principal.owns(null)).isFalse();
    }

    private void givenCallerIs(Tutor caller) {
        given(currentUser.requireId()).willReturn(CALLER_ID);
        given(users.findById(CALLER_ID)).willReturn(Optional.of(caller));
    }

    private static Tutor activeTutor() {
        Tutor tutor = new Tutor("nimali@example.lk", "$2a$10$hash", "Nimali Perera", "+94771234567", Language.SI);
        ReflectionTestUtils.setField(tutor, "id", CALLER_ID);
        tutor.verifyEmail();
        tutor.verifyPhone();
        tutor.activate();
        return tutor;
    }
}
