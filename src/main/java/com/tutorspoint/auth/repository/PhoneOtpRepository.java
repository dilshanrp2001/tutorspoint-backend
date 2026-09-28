package com.tutorspoint.auth.repository;

import com.tutorspoint.auth.domain.PhoneOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * One-time phone codes (FR-A2).
 *
 * <p>Codes are salted-hashed, so there is no digest to search by: the live code for a
 * user is the most recent row, and the candidate is compared against it by the service.
 */
public interface PhoneOtpRepository extends JpaRepository<PhoneOtp, Long> {

    Optional<PhoneOtp> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    /** Backs the send-rate cap — every SMS costs money, so resends are throttled. */
    long countByUserIdAndCreatedAtAfter(Long userId, Instant since);
}
