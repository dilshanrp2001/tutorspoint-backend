package com.tutorspoint.admin;

import com.tutorspoint.admin.dto.AdminUserCriteria;
import com.tutorspoint.admin.dto.AdminUserPage;
import com.tutorspoint.admin.dto.AdminUserSummary;
import com.tutorspoint.admin.event.AccountModeratedEvent;
import com.tutorspoint.admin.repository.AdminUserRepository;
import com.tutorspoint.auth.RefreshTokenService;
import com.tutorspoint.auth.domain.AccountStatus;
import com.tutorspoint.auth.domain.Admin;
import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.Role;
import com.tutorspoint.auth.domain.User;
import com.tutorspoint.auth.security.CurrentUser;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import com.tutorspoint.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

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
 * Account moderation with the repository, the session store and the event bus mocked.
 *
 * <p>The assertions that matter most are on what else happens: a suspension ends every session
 * and publishes the event the audit record is written from, and a refused or repeated action
 * does neither.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long USER_ID = 42L;

    @Mock
    private AdminUserRepository users;

    @Mock
    private RefreshTokenService refreshTokens;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private ApplicationEventPublisher events;

    private AdminUserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminUserServiceImpl(users, refreshTokens, new AdminMapperImpl(), currentUser, events);
        lenient().when(currentUser.requireId()).thenReturn(ADMIN_ID);
    }

    @Test
    @DisplayName("the list is newest first, one page at a time, with the filters passed to the query")
    void listsAPage() {
        when(users.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<User>(List.of(activeParent()), call.getArgument(1), 31));

        AdminUserPage page = service.users(new AdminUserCriteria(Role.PARENT, AccountStatus.ACTIVE, "niluka", false, 1, 10));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(users).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        assertThat(page.total()).isEqualTo(31);
        assertThat(page.users()).extracting(AdminUserSummary::email).containsExactly("niluka@example.lk");
    }

    @Test
    @DisplayName("suspending blocks the account, ends every session, and publishes the audited event")
    void suspends() {
        Parent parent = activeParent();
        when(users.findById(USER_ID)).thenReturn(Optional.of(parent));

        AdminUserSummary result = service.suspend(USER_ID, "Spamming tutors");

        assertThat(result.status()).isEqualTo(AccountStatus.SUSPENDED);
        verify(refreshTokens).revokeAllFor(USER_ID);
        verify(events).publishEvent(new AccountModeratedEvent(ADMIN_ID, USER_ID,
                AccountStatus.ACTIVE, AccountStatus.SUSPENDED, "Spamming tutors"));
    }

    @Test
    @DisplayName("suspending an account that is already suspended changes and records nothing")
    void suspendingTwiceIsANoOp() {
        Parent parent = activeParent();
        parent.suspend();
        when(users.findById(USER_ID)).thenReturn(Optional.of(parent));

        service.suspend(USER_ID, "again");

        verify(refreshTokens, never()).revokeAllFor(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("an administrator's account cannot be suspended from here, including one's own")
    void adminsAreNotModeratable() {
        Admin admin = new Admin("ops@tutorspoint.lk", "hash", "Ops", "+94700000001", Language.EN);
        when(users.findById(USER_ID)).thenReturn(Optional.of(admin));

        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(() -> service.suspend(USER_ID, "oops"))
                .satisfies(e -> assertThat(e.getCode()).isEqualTo("ADMIN_ACCOUNT_NOT_MODERATABLE"));
        assertThat(admin.getStatus()).isNotEqualTo(AccountStatus.SUSPENDED);
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("an unknown account is not found")
    void unknownAccount() {
        when(users.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> service.suspend(USER_ID, "x"));
    }

    @Test
    @DisplayName("reinstating returns the account to where verification had got to, and is audited")
    void reinstates() {
        Parent parent = activeParent();
        parent.suspend();
        when(users.findById(USER_ID)).thenReturn(Optional.of(parent));

        AdminUserSummary result = service.reinstate(USER_ID, null);

        assertThat(result.status()).isEqualTo(AccountStatus.ACTIVE);
        verify(events).publishEvent(new AccountModeratedEvent(ADMIN_ID, USER_ID,
                AccountStatus.SUSPENDED, AccountStatus.ACTIVE, null));
    }

    @Test
    @DisplayName("reinstating an account that is not suspended is refused and records nothing")
    void reinstatingAnActiveAccountIsRefused() {
        when(users.findById(USER_ID)).thenReturn(Optional.of(activeParent()));

        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(() -> service.reinstate(USER_ID, null));
        verify(events, never()).publishEvent(any());
    }

    private static Parent activeParent() {
        Parent parent = new Parent("niluka@example.lk", "hash", "Niluka Fernando", "+94771234567", Language.EN);
        parent.verifyEmail();
        parent.verifyPhone();
        parent.activate();
        return parent;
    }
}
