package com.tutorspoint.auth.repository;

import com.tutorspoint.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Accounts of every role. Spring Data resolves the concrete subtype (Tutor, Parent, Student,
 * Admin) when it loads the row, so a caller that only needs the shared contract can work
 * with {@link User} and never cares which table the rest of the row came from.
 *
 * <p>Lookups by email assume the stored value is already lower-cased, which
 * {@code User} guarantees on the way in; callers must normalise the value they search
 * with the same way.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    /** Registration guard: the unique index is the backstop, this is the friendly error. */
    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);
}
