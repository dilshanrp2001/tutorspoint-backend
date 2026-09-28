package com.tutorspoint.auth.repository;

import com.tutorspoint.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Sign-in sessions (FR-A4).
 *
 * <p>The client sends back only the token, so the digest is the lookup key. The
 * by-user finder exists for the operations that must end every session at once —
 * password reset and account deletion — and returns entities rather than running a bulk
 * update so that revocation stays an intention-revealing method on the entity.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);
}
