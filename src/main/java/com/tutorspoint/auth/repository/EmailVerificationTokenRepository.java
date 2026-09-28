package com.tutorspoint.auth.repository;

import com.tutorspoint.auth.domain.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Email verification tokens (FR-A3).
 *
 * <p>The emailed link carries only the token, so the digest is the lookup key — which
 * works because that digest is deterministic (SHA-256), unlike the salted OTP hash.
 */
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /** Re-sending a link invalidates nothing by itself; the newest one is the live one. */
    Optional<EmailVerificationToken> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
