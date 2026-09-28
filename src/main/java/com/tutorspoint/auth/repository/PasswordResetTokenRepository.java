package com.tutorspoint.auth.repository;

import com.tutorspoint.auth.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Password-reset tokens (FR-A6). Looked up by digest, like the email verification token
 * and for the same reason: the emailed link carries the token and nothing else.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
