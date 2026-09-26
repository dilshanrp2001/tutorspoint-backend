package com.tutorspoint.auth.domain;

import com.tutorspoint.common.domain.Language;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * A learner looking for a tutor for themselves (PRD FR-A8).
 *
 * <p>Everything a {@link Parent} may do on the demand side, a student may do too — search,
 * shortlist, enquire — because those belong to {@link Seeker}. What a student does not have
 * is children: an enquiry from a student is about the student, so there is no sub-profile to
 * name. There is no age gate either; the child-safety controls are the ones every enquiry
 * already carries (masked contact details until the tutor replies, a logged thread, and admin
 * suspension).
 */
@Entity
@Table(name = "students")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Student extends Seeker {

    public Student(String email,
                   String passwordHash,
                   String fullName,
                   String phoneNumber,
                   Language preferredLanguage) {
        super(Role.STUDENT, email, passwordHash, fullName, phoneNumber, preferredLanguage);
    }
}
