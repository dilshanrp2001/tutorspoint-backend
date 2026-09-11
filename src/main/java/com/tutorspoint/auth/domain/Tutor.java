package com.tutorspoint.auth.domain;

import com.tutorspoint.common.domain.Language;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * A tutor account — the identity half of the supply side.
 *
 * <p>Deliberately holds nothing beyond the shared {@link User} contract for now. What a
 * tutor advertises (subjects, syllabuses, fees, areas served, verification state) belongs
 * to {@code TutorProfile} in the {@code tutor} package, which has a different lifecycle
 * from the account: the account exists from registration, the profile is drafted,
 * published and unpublished independently.
 */
@Entity
@Table(name = "tutors")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tutor extends User {

    public Tutor(String email,
                 String passwordHash,
                 String fullName,
                 String phoneNumber,
                 Language preferredLanguage) {
        super(Role.TUTOR, email, passwordHash, fullName, phoneNumber, preferredLanguage);
    }
}
