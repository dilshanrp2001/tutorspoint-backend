package com.tutorspoint.auth;

import com.tutorspoint.auth.config.JwtProperties;
import com.tutorspoint.auth.domain.RefreshToken;
import com.tutorspoint.auth.repository.RefreshTokenRepository;
import com.tutorspoint.auth.security.SecureTokens;
import com.tutorspoint.common.exception.AuthenticationFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final String ERROR_INVALID = "REFRESH_TOKEN_INVALID";

    private final RefreshTokenRepository refreshTokens;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    @Override
    @Transactional
    public String issueFor(Long userId) {
        String token = SecureTokens.newLinkToken();
        Instant now = clock.instant();
        refreshTokens.save(new RefreshToken(
                userId, SecureTokens.sha256Hex(token), now.plus(jwtProperties.refreshTokenTtl())));
        return token;
    }

    /**
     * Replay detection: a refresh token is rotated out the moment it is used, so seeing a
     * revoked one again means either a client bug or a stolen token being used in parallel
     * with the real one. There is no way to tell which, so the safe reading is theft and
     * every session for that account is ended — the legitimate user signs in again, the
     * thief gets nothing.
     */
    @Override
    @Transactional
    public RefreshToken requireActive(String refreshToken) {
        RefreshToken session = refreshTokens.findByTokenHash(SecureTokens.sha256Hex(refreshToken))
                .orElseThrow(() -> new AuthenticationFailedException(ERROR_INVALID,
                        "Refresh token is not valid"));

        if (session.isRevoked()) {
            log.warn("Revoked refresh token replayed for account {}; ending every session",
                    session.getUserId());
            revokeAllFor(session.getUserId());
            throw new AuthenticationFailedException(ERROR_INVALID, "Refresh token is not valid");
        }
        if (!session.isActive(clock.instant())) {
            throw new AuthenticationFailedException(ERROR_INVALID, "Refresh token has expired");
        }
        return session;
    }

    @Override
    @Transactional
    public void revoke(RefreshToken session) {
        session.revoke(clock.instant());
        refreshTokens.save(session);
    }

    @Override
    @Transactional
    public void revokeIfPresent(String refreshToken) {
        Optional<RefreshToken> session = refreshTokens.findByTokenHash(SecureTokens.sha256Hex(refreshToken));
        session.filter(found -> !found.isRevoked()).ifPresent(this::revoke);
    }

    @Override
    @Transactional
    public void revokeAllFor(Long userId) {
        List<RefreshToken> live = refreshTokens.findByUserIdAndRevokedAtIsNull(userId);
        Instant now = clock.instant();
        live.forEach(session -> session.revoke(now));
        refreshTokens.saveAll(live);
        if (!live.isEmpty()) {
            log.info("Ended {} session(s) for account {}", live.size(), userId);
        }
    }
}
