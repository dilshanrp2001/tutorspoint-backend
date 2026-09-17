package com.tutorspoint.auth.domain;

import com.tutorspoint.common.domain.Language;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The demand side: an account that looks for a tutor. Abstract — every seeker is a
 * {@link Parent} acting for their children or a {@link Student} acting for themselves.
 *
 * <p>Its own JOINED level rather than two unrelated subtypes of {@link User}, because what
 * the demand side owns — enquiries, shortlists, and later reviews and requests — belongs to
 * either kind alike. Those point at this type, so the code that serves them never asks which
 * kind it holds (LSP), and the database still refuses a tutor or an admin in their place.
 *
 * <p>Holds nothing of its own yet. What differs between the two kinds lives on the subtypes:
 * child sub-profiles are a {@link Parent} concern, and a {@link Student} simply has none.
 */
@Entity
@Table(name = "seekers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Seeker extends User {

    protected Seeker(Role role,
                     String email,
                     String passwordHash,
                     String fullName,
                     String phoneNumber,
                     Language preferredLanguage) {
        super(role, email, passwordHash, fullName, phoneNumber, preferredLanguage);
    }
}
