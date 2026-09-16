package com.tutorspoint.admin.bootstrap;

import com.tutorspoint.auth.domain.Admin;
import com.tutorspoint.auth.domain.Role;
import com.tutorspoint.auth.repository.UserRepository;
import com.tutorspoint.common.domain.Language;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Provisioning an administrator.
 *
 * <p>The account is created already verified and active. That is not a shortcut around
 * verification: the email and phone number are asserted by the operator who set the environment,
 * which is a stronger check than a link and a code, and an administrator who had to receive an
 * SMS before the SMS gateway was configured could never sign in.
 */
@Service
@RequiredArgsConstructor
public class AdminProvisioningServiceImpl implements AdminProvisioningService {

    /**
     * Longer than the 8 a user may choose. This password opens every tutor's identity document,
     * and it is typed once into a secret store rather than remembered.
     */
    static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public Outcome provision(String email, String password, String fullName, String phoneNumber) {
        requireText(email, "email");
        requireText(fullName, "fullName");
        requireText(phoneNumber, "phoneNumber");
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "The bootstrap admin password must be at least %d characters".formatted(MIN_PASSWORD_LENGTH));
        }

        String normalisedEmail = email.trim().toLowerCase(Locale.ROOT);
        var existing = users.findByEmail(normalisedEmail);
        if (existing.isPresent()) {
            return existing.get().getRole() == Role.ADMIN ? Outcome.ALREADY_ADMIN : Outcome.CONFLICT;
        }
        if (users.existsByPhoneNumber(phoneNumber.trim())) {
            return Outcome.CONFLICT;
        }

        Admin admin = new Admin(normalisedEmail, passwordEncoder.encode(password), fullName, phoneNumber, Language.EN);
        admin.verifyEmail();
        admin.verifyPhone();
        admin.activate();
        users.save(admin);
        return Outcome.CREATED;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The bootstrap admin " + name + " must be set");
        }
    }
}
