package com.tutorspoint.auth.domain;

import com.tutorspoint.common.domain.Language;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * A platform administrator: moderation, verification decisions, reference data.
 *
 * <p>Holds nothing beyond the shared {@link User} contract — what an admin may do is an
 * authorization concern carried by {@link Role#ADMIN}, not extra state on the account.
 * Admins are provisioned, never self-registered.
 */
@Entity
@Table(name = "admins")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admin extends User {

    public Admin(String email,
                 String passwordHash,
                 String fullName,
                 String phoneNumber,
                 Language preferredLanguage) {
        super(Role.ADMIN, email, passwordHash, fullName, phoneNumber, preferredLanguage);
    }
}
