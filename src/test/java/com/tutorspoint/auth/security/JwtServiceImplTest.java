package com.tutorspoint.auth.security;

import com.tutorspoint.auth.config.JwtProperties;
import com.tutorspoint.auth.domain.Role;
import com.tutorspoint.auth.domain.Tutor;
import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.AuthenticationFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * The token adapter: what it puts in a token, and every reason it refuses one.
 *
 * <p>Signing and verifying are exercised for real rather than mocked — a test that stubbed the
 * library would prove nothing about whether a forged or expired token is actually rejected,
 * which is the only thing this class exists to get right.
 */
class JwtServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-09-12T09:00:00Z");
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);

    private static final String SECRET = "dGVzdC1vbmx5LXNpZ25pbmcta2V5LW5vdC1hLXNlY3JldCE=";
    private static final String OTHER_SECRET = "YW5vdGhlci10ZXN0LWtleS10aGF0LWlzLWRpZmZlcmVudCE=";

    private final JwtServiceImpl service = serviceAt(NOW, SECRET, "tutorspoint");

    @Test
    void aTokenRoundTripsTheIdentityItWasIssuedFor() {
        String token = service.issueAccessToken(tutor(7L));

        AuthenticatedUser caller = service.parse(token);

        assertThat(caller.userId()).isEqualTo(7L);
        assertThat(caller.email()).isEqualTo("nimali@example.lk");
        assertThat(caller.role()).isEqualTo(Role.TUTOR);
        assertThat(caller.authorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_TUTOR");
    }

    @Test
    void aTokenPastItsLifetimeIsReportedAsExpiredSoTheClientKnowsToRefresh() {
        String token = service.issueAccessToken(tutor(7L));
        JwtServiceImpl later = serviceAt(NOW.plus(ACCESS_TTL).plusSeconds(1), SECRET, "tutorspoint");

        assertThatExceptionOfType(AuthenticationFailedException.class)
                .isThrownBy(() -> later.parse(token))
                .satisfies(e -> assertThat(e.getCode()).isEqualTo("ACCESS_TOKEN_EXPIRED"));
    }

    @Test
    void aTokenIsStillGoodTheInstantBeforeItExpires() {
        String token = service.issueAccessToken(tutor(7L));
        JwtServiceImpl later = serviceAt(NOW.plus(ACCESS_TTL).minusSeconds(1), SECRET, "tutorspoint");

        assertThat(later.parse(token).userId()).isEqualTo(7L);
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRefused() {
        String forged = serviceAt(NOW, OTHER_SECRET, "tutorspoint").issueAccessToken(tutor(7L));

        assertThatExceptionOfType(AuthenticationFailedException.class)
                .isThrownBy(() -> service.parse(forged))
                .satisfies(e -> assertThat(e.getCode()).isEqualTo("ACCESS_TOKEN_INVALID"));
    }

    @Test
    void aTokenMintedForAnotherSystemIsRefused() {
        String elsewhere = serviceAt(NOW, SECRET, "some-other-app").issueAccessToken(tutor(7L));

        assertThatExceptionOfType(AuthenticationFailedException.class)
                .isThrownBy(() -> service.parse(elsewhere))
                .satisfies(e -> assertThat(e.getCode()).isEqualTo("ACCESS_TOKEN_INVALID"));
    }

    @Test
    void somethingThatIsNotATokenAtAllIsRefused() {
        assertThatExceptionOfType(AuthenticationFailedException.class)
                .isThrownBy(() -> service.parse("not-a-token"))
                .satisfies(e -> assertThat(e.getCode()).isEqualTo("ACCESS_TOKEN_INVALID"));
    }

    @Test
    void theClientIsToldHowLongAnAccessTokenLasts() {
        assertThat(service.accessTokenTtl()).isEqualTo(ACCESS_TTL);
    }

    @Test
    void aSecretTooShortForHs256StopsTheApplicationStarting() {
        assertThatIllegalStateException()
                .isThrownBy(() -> serviceAt(NOW, "c2hvcnQ=", "tutorspoint"))
                .withMessageContaining("at least 32 bytes");
    }

    @Test
    void aSecretThatIsNotBase64StopsTheApplicationStarting() {
        assertThatIllegalStateException()
                .isThrownBy(() -> serviceAt(NOW, "not base64 at all!!", "tutorspoint"))
                .withMessageContaining("openssl rand -base64 32");
    }

    private static JwtServiceImpl serviceAt(Instant now, String secret, String issuer) {
        return new JwtServiceImpl(
                new JwtProperties(secret, issuer, ACCESS_TTL, Duration.ofDays(30)),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static Tutor tutor(Long id) {
        Tutor tutor = new Tutor("nimali@example.lk", "$2a$10$hash", "Nimali Perera", "+94771234567", Language.SI);
        ReflectionTestUtils.setField(tutor, "id", id);
        return tutor;
    }
}
