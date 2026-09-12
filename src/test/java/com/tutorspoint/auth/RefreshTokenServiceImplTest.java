package com.tutorspoint.auth;

import com.tutorspoint.auth.config.JwtProperties;
import com.tutorspoint.auth.domain.RefreshToken;
import com.tutorspoint.auth.repository.RefreshTokenRepository;
import com.tutorspoint.auth.security.SecureTokens;
import com.tutorspoint.common.exception.AuthenticationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/** Sessions: how one is opened, how one is validated, and what a replayed token costs. */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-09-12T09:00:00Z");
    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    private static final String RAW_TOKEN = "a-refresh-token-the-client-holds";
    private static final Long USER_ID = 7L;

    private static final JwtProperties PROPERTIES = new JwtProperties(
            "dGVzdC1vbmx5LXNpZ25pbmcta2V5LW5vdC1hLXNlY3JldCE=",
            "tutorspoint",
            Duration.ofMinutes(15),
            REFRESH_TTL);

    @Mock
    private RefreshTokenRepository refreshTokens;

    private RefreshTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenServiceImpl(refreshTokens, PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void issuingStoresOnlyTheDigestAndReturnsTheTokenOnce() {
        String token = service.issueFor(USER_ID);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        then(refreshTokens).should().save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(SecureTokens.sha256Hex(token))
                .isNotEqualTo(token);
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(REFRESH_TTL));
        assertThat(saved.getValue().isRevoked()).isFalse();
    }

    @Test
    void aLiveSessionIsFoundByItsDigest() {
        RefreshToken session = liveSession();
        givenStored(session);

        assertThat(service.requireActive(RAW_TOKEN)).isSameAs(session);
    }

    @Test
    void anUnknownTokenIsRefused() {
        given(refreshTokens.findByTokenHash(SecureTokens.sha256Hex(RAW_TOKEN))).willReturn(Optional.empty());

        assertThatExceptionOfType(AuthenticationFailedException.class)
                .isThrownBy(() -> service.requireActive(RAW_TOKEN))
                .satisfies(e -> assertThat(e.getCode()).isEqualTo("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void anExpiredTokenIsRefused() {
        RefreshToken session = new RefreshToken(USER_ID, SecureTokens.sha256Hex(RAW_TOKEN), NOW.minusSeconds(1));
        givenStored(session);

        assertThatExceptionOfType(AuthenticationFailedException.class)
                .isThrownBy(() -> service.requireActive(RAW_TOKEN))
                .satisfies(e -> assertThat(e.getCode()).isEqualTo("REFRESH_TOKEN_INVALID"));
    }

    /**
     * The security property that makes rotation worth having: because a used token is dead,
     * seeing one again is evidence that two parties hold it, and the only safe response is to
     * end every session rather than guess which one is the thief.
     */
    @Test
    void replayingARevokedTokenEndsEverySessionForThatAccount() {
        RefreshToken replayed = liveSession();
        replayed.revoke(NOW.minusSeconds(30));
        givenStored(replayed);
        RefreshToken otherLiveSession = new RefreshToken(USER_ID, "another-digest", NOW.plus(REFRESH_TTL));
        given(refreshTokens.findByUserIdAndRevokedAtIsNull(USER_ID)).willReturn(List.of(otherLiveSession));

        assertThatExceptionOfType(AuthenticationFailedException.class)
                .isThrownBy(() -> service.requireActive(RAW_TOKEN))
                .satisfies(e -> assertThat(e.getCode()).isEqualTo("REFRESH_TOKEN_INVALID"));

        assertThat(otherLiveSession.isRevoked()).isTrue();
    }

    @Test
    void revokingStampsTheMomentTheSessionEnded() {
        RefreshToken session = liveSession();

        service.revoke(session);

        assertThat(session.getRevokedAt()).isEqualTo(NOW);
        then(refreshTokens).should().save(session);
    }

    @Test
    void loggingOutWithAnUnknownTokenIsNotAnError() {
        given(refreshTokens.findByTokenHash(SecureTokens.sha256Hex(RAW_TOKEN))).willReturn(Optional.empty());

        assertThatCode(() -> service.revokeIfPresent(RAW_TOKEN)).doesNotThrowAnyException();

        then(refreshTokens).should(never()).save(any(RefreshToken.class));
    }

    @Test
    void loggingOutTwiceIsNotAnError() {
        RefreshToken session = liveSession();
        session.revoke(NOW.minusSeconds(30));
        givenStored(session);

        assertThatCode(() -> service.revokeIfPresent(RAW_TOKEN)).doesNotThrowAnyException();

        then(refreshTokens).should(never()).save(any(RefreshToken.class));
    }

    @Test
    void endingEverySessionTouchesOnlyTheLiveOnes() {
        RefreshToken first = new RefreshToken(USER_ID, "digest-1", NOW.plus(REFRESH_TTL));
        RefreshToken second = new RefreshToken(USER_ID, "digest-2", NOW.plus(REFRESH_TTL));
        given(refreshTokens.findByUserIdAndRevokedAtIsNull(USER_ID)).willReturn(List.of(first, second));

        service.revokeAllFor(USER_ID);

        assertThat(first.getRevokedAt()).isEqualTo(NOW);
        assertThat(second.getRevokedAt()).isEqualTo(NOW);
        then(refreshTokens).should().saveAll(List.of(first, second));
    }

    private static RefreshToken liveSession() {
        return new RefreshToken(USER_ID, SecureTokens.sha256Hex(RAW_TOKEN), NOW.plus(REFRESH_TTL));
    }

    private void givenStored(RefreshToken session) {
        given(refreshTokens.findByTokenHash(SecureTokens.sha256Hex(RAW_TOKEN)))
                .willReturn(Optional.of(session));
    }
}
