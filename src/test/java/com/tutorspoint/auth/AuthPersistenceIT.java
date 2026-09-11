package com.tutorspoint.auth;

import com.tutorspoint.AbstractIntegrationTest;
import com.tutorspoint.auth.domain.AccountStatus;
import com.tutorspoint.auth.domain.ChildProfile;
import com.tutorspoint.auth.domain.EmailVerificationToken;
import com.tutorspoint.auth.domain.Parent;
import com.tutorspoint.auth.domain.PhoneOtp;
import com.tutorspoint.auth.domain.Role;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.auth.domain.User;
import com.tutorspoint.auth.repository.ChildProfileRepository;
import com.tutorspoint.auth.repository.EmailVerificationTokenRepository;
import com.tutorspoint.auth.repository.PhoneOtpRepository;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.common.domain.Language;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Proves the entities survive a round trip through the hand-written schema. Validation at
 * start-up only compares columns; this inserts and reads back, which is what catches a
 * broken JOINED subtype, a cascade that does not fire, or a constraint the code relies on
 * but the migration never created.
 */
@Transactional
class AuthPersistenceIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private ChildProfileRepository childProfiles;

    @Autowired
    private EmailVerificationTokenRepository emailTokens;

    @Autowired
    private PhoneOtpRepository phoneOtps;

    @Autowired
    private EntityManager entityManager;

    @Test
    void aTutorRoundTripsAsItsOwnSubtype() {
        Tutor saved = users.saveAndFlush(
                new Tutor("nimali@example.lk", "$2a$10$hash", "Nimali Perera", "+94771111111", Language.SI));
        detach();

        User loaded = users.findByEmail("nimali@example.lk").orElseThrow();

        assertThat(loaded).isInstanceOf(Tutor.class);
        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.getRole()).isEqualTo(Role.TUTOR);
        assertThat(loaded.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(loaded.getPreferredLanguage()).isEqualTo(Language.SI);
        assertThat(loaded.isEmailVerified()).isFalse();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void activationSurvivesAReload() {
        Tutor tutor = new Tutor("kasun@example.lk", "$2a$10$hash", "Kasun", "+94772222222", Language.EN);
        tutor.verifyEmail();
        tutor.verifyPhone();
        tutor.activate();
        Instant signedInAt = Instant.parse("2026-09-11T10:15:30Z");
        tutor.recordLogin(signedInAt);
        Long id = users.saveAndFlush(tutor).getId();
        detach();

        User loaded = users.findById(id).orElseThrow();

        assertThat(loaded.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(loaded.isFullyVerified()).isTrue();
        assertThat(loaded.getLastLoginAt()).isEqualTo(signedInAt);
    }

    @Test
    void childrenAreSavedAndRemovedWithTheirParent() {
        Parent parent = new Parent("kamal@example.lk", "$2a$10$hash", "Kamal Silva", "+94773333333", Language.TA);
        parent.addChild("Sanduni", "Grade 11", "GCE O/L", "Visakha Vidyalaya", null);
        parent.addChild("Dinuka", "Grade 8", "Grade 6-9", null, "Maths only");
        Long parentId = users.saveAndFlush(parent).getId();
        detach();

        assertThat(childProfiles.findByParentIdOrderByNameAsc(parentId))
                .extracting(ChildProfile::getName)
                .containsExactly("Dinuka", "Sanduni");

        Parent reloaded = (Parent) users.findById(parentId).orElseThrow();
        ChildProfile dinuka = childProfiles.findByParentIdOrderByNameAsc(parentId).getFirst();
        reloaded.removeChild(dinuka);
        users.saveAndFlush(reloaded);
        detach();

        assertThat(childProfiles.findByParentIdOrderByNameAsc(parentId))
                .extracting(ChildProfile::getName)
                .containsExactly("Sanduni");
        assertThat(childProfiles.findByIdAndParentId(dinuka.getId(), parentId)).isEmpty();
    }

    @Test
    void theDatabaseRefusesASecondAccountOnTheSameEmail() {
        users.saveAndFlush(new Tutor("shared@example.lk", "$2a$10$hash", "First", "+94774444444", Language.EN));

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                users.saveAndFlush(
                        new Parent("shared@example.lk", "$2a$10$hash", "Second", "+94775555555", Language.EN)));
    }

    @Test
    void verificationSecretsAreFoundTheWayTheServiceWillLookThemUp() {
        Long userId = users.saveAndFlush(
                new Tutor("tokens@example.lk", "$2a$10$hash", "Tokens", "+94776666666", Language.EN)).getId();
        Instant now = Instant.parse("2026-09-11T10:00:00Z");
        emailTokens.saveAndFlush(new EmailVerificationToken(userId, "b".repeat(64), now.plus(Duration.ofHours(24))));
        phoneOtps.saveAndFlush(new PhoneOtp(userId, "$2a$10$otpdigest", now.plus(Duration.ofMinutes(5))));
        detach();

        assertThat(emailTokens.findByTokenHash("b".repeat(64))).isPresent();
        assertThat(phoneOtps.findFirstByUserIdOrderByCreatedAtDesc(userId))
                .get()
                .satisfies(otp -> {
                    assertThat(otp.getAttemptCount()).isZero();
                    assertThat(otp.getConsumedAt()).isNull();
                });
        assertThat(phoneOtps.countByUserIdAndCreatedAtAfter(userId, Instant.now().minus(Duration.ofHours(1))))
                .isEqualTo(1);
    }

    /** Forces the next read to come from the database rather than the persistence context. */
    private void detach() {
        entityManager.flush();
        entityManager.clear();
    }
}
