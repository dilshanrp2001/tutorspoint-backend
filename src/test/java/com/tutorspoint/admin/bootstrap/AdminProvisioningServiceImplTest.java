package com.tutorspoint.admin.bootstrap;

import com.tutorspoint.admin.bootstrap.AdminProvisioningService.Outcome;
import com.tutorspoint.auth.domain.AccountStatus;
import com.tutorspoint.auth.domain.Admin;
import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.User;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.common.domain.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminProvisioningServiceImplTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Mock
    private UserRepository users;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminProvisioningServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminProvisioningServiceImpl(users, passwordEncoder);
        lenient().when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$10$digest");
    }

    @Test
    @DisplayName("creates an active admin, verified on both channels, storing only the digest")
    void createsAnActiveAdmin() {
        when(users.findByEmail("ops@tutorspoint.lk")).thenReturn(Optional.empty());

        Outcome outcome = service.provision(" Ops@TutorsPoint.lk ", PASSWORD, "Pilot Ops", "+94700000001");

        assertThat(outcome).isEqualTo(Outcome.CREATED);
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue()).isInstanceOf(Admin.class);
        assertThat(saved.getValue().getEmail()).isEqualTo("ops@tutorspoint.lk");
        assertThat(saved.getValue().getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$10$digest");
    }

    @Test
    @DisplayName("an existing admin is left exactly as it is - a restart never resets a password")
    void existingAdminIsUntouched() {
        Admin existing = new Admin("ops@tutorspoint.lk", "$2a$10$old", "Pilot Ops", "+94700000001", Language.EN);
        when(users.findByEmail("ops@tutorspoint.lk")).thenReturn(Optional.of(existing));

        assertThat(service.provision("ops@tutorspoint.lk", PASSWORD, "Pilot Ops", "+94700000001"))
                .isEqualTo(Outcome.ALREADY_ADMIN);
        assertThat(existing.getPasswordHash()).isEqualTo("$2a$10$old");
        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("an email that belongs to a parent is not turned into an admin")
    void doesNotPromoteAnExistingUser() {
        when(users.findByEmail("ops@tutorspoint.lk"))
                .thenReturn(Optional.of(new Parent("ops@tutorspoint.lk", "h", "Someone", "+94700000009", Language.EN)));

        assertThat(service.provision("ops@tutorspoint.lk", PASSWORD, "Pilot Ops", "+94700000001"))
                .isEqualTo(Outcome.CONFLICT);
        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("a phone number already in use is a conflict, not a unique-index crash at startup")
    void phoneNumberTaken() {
        when(users.findByEmail("ops@tutorspoint.lk")).thenReturn(Optional.empty());
        when(users.existsByPhoneNumber("+94700000001")).thenReturn(true);

        assertThat(service.provision("ops@tutorspoint.lk", PASSWORD, "Pilot Ops", "+94700000001"))
                .isEqualTo(Outcome.CONFLICT);
    }

    @Test
    @DisplayName("a short or missing password stops provisioning before anything is looked up")
    void refusesAWeakPassword() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.provision("ops@tutorspoint.lk", "short", "Pilot Ops", "+94700000001"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.provision("ops@tutorspoint.lk", null, "Pilot Ops", "+94700000001"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.provision("ops@tutorspoint.lk", PASSWORD, "Pilot Ops", " "));
        verify(users, never()).findByEmail(any());
    }
}
