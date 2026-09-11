package com.tutorspoint.auth.domain;

import com.tutorspoint.common.domain.BaseEntity;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;

/**
 * A person with an account. Abstract: every real user is a {@link Tutor}, a
 * {@link Parent} or an {@link Admin}, and the subtype is fixed at registration.
 *
 * <p>JOINED inheritance keeps the shared identity in one {@code users} row and each
 * subtype's own data in its own table, so the shared columns are declared, constrained
 * and indexed exactly once. Every subtype honours this contract in full — none throws
 * "unsupported" for a method declared here (LSP).
 *
 * <p>There are no setters. An account moves between states only through the
 * intention-revealing methods below, each of which refuses a transition the domain
 * forbids rather than trusting its caller.
 */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class User extends BaseEntity {

    /** Stable keys the client switches on and translates into si / ta / en. */
    private static final String ERROR_ACCOUNT_DELETED = "ACCOUNT_DELETED";
    private static final String ERROR_VERIFICATION_INCOMPLETE = "ACCOUNT_VERIFICATION_INCOMPLETE";
    private static final String ERROR_ACCOUNT_NOT_ACTIVE = "ACCOUNT_NOT_ACTIVE";

    /** Lower-cased on the way in, so the unique index is genuinely case-insensitive. */
    @Column(name = "email", nullable = false, length = 254)
    private String email;

    /** BCrypt digest. The plaintext password never reaches this class. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    /** E.164, for example +94771234567. Unique: one account per number. */
    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private Role role;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;

    /** Drives notification templates and the language of API messages. */
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", nullable = false, length = 2)
    private Language preferredLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /**
     * A new registration: unverified on both channels and therefore
     * {@link AccountStatus#PENDING_VERIFICATION}. Only a subtype may call this, which is
     * what guarantees {@code role} can never disagree with the concrete class.
     */
    protected User(Role role,
                   String email,
                   String passwordHash,
                   String fullName,
                   String phoneNumber,
                   Language preferredLanguage) {
        this.role = required(role, "role");
        this.email = normaliseEmail(email);
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.fullName = requireText(fullName, "fullName").trim();
        this.phoneNumber = requireText(phoneNumber, "phoneNumber").trim();
        this.preferredLanguage = required(preferredLanguage, "preferredLanguage");
        this.status = AccountStatus.PENDING_VERIFICATION;
        this.emailVerified = false;
        this.phoneVerified = false;
    }

    /** Confirms the emailed link was followed (FR-A3). Idempotent: users click twice. */
    public void verifyEmail() {
        ensureNotDeleted();
        this.emailVerified = true;
    }

    /** Confirms the SMS one-time code was entered correctly (FR-A2). Idempotent. */
    public void verifyPhone() {
        ensureNotDeleted();
        this.phoneVerified = true;
    }

    /**
     * Opens the account for use, or reinstates a suspended one.
     *
     * @throws BusinessRuleViolationException if either channel is still unverified, or
     *                                        the account has been deleted
     */
    public void activate() {
        ensureNotDeleted();
        if (!isFullyVerified()) {
            throw new BusinessRuleViolationException(ERROR_VERIFICATION_INCOMPLETE,
                    "Account %s cannot be activated until both email and phone are verified"
                            .formatted(getId()));
        }
        this.status = AccountStatus.ACTIVE;
    }

    /** Blocks the account (moderation). Reversible through {@link #activate()}. */
    public void suspend() {
        ensureNotDeleted();
        this.status = AccountStatus.SUSPENDED;
    }

    /**
     * Soft-deletes the account at the user's request (FR-A7). Terminal: the row survives
     * for referential integrity and audit, but no transition leads back out.
     */
    public void delete() {
        this.status = AccountStatus.DELETED;
    }

    /**
     * Stamps a successful sign-in.
     *
     * @throws BusinessRuleViolationException if the account is not active — a pending,
     *                                        suspended or deleted user cannot sign in
     */
    public void recordLogin(Instant at) {
        if (status != AccountStatus.ACTIVE) {
            throw new BusinessRuleViolationException(ERROR_ACCOUNT_NOT_ACTIVE,
                    "Account %s is %s and cannot sign in".formatted(getId(), status));
        }
        this.lastLoginAt = required(at, "at");
    }

    /** Replaces the stored digest (password change or reset). Never takes plaintext. */
    public void changePassword(String newPasswordHash) {
        ensureNotDeleted();
        this.passwordHash = requireText(newPasswordHash, "newPasswordHash");
    }

    public void changePreferredLanguage(Language language) {
        ensureNotDeleted();
        this.preferredLanguage = required(language, "language");
    }

    /** True once both channels are confirmed — the precondition {@link #activate()} enforces. */
    public boolean isFullyVerified() {
        return emailVerified && phoneVerified;
    }

    /** Guard for this class and its subtypes: nothing may be changed once deleted. */
    protected void ensureNotDeleted() {
        if (status == AccountStatus.DELETED) {
            throw new BusinessRuleViolationException(ERROR_ACCOUNT_DELETED,
                    "Account %s has been deleted".formatted(getId()));
        }
    }

    private static String normaliseEmail(String email) {
        return requireText(email, "email").trim().toLowerCase(Locale.ROOT);
    }

    /**
     * A bad argument here is a programming error, not user error: request input is
     * rejected by Bean Validation on the DTO long before it reaches the entity.
     */
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }
}
