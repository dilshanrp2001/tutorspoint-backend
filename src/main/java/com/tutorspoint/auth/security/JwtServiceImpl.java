package com.tutorspoint.auth.security;

import com.tutorspoint.auth.config.JwtProperties;
import com.tutorspoint.auth.domain.Role;
import com.tutorspoint.auth.domain.User;
import com.tutorspoint.common.exception.AuthenticationFailedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * The only class in the application that knows what a JWT is.
 *
 * <p>HS256 with a symmetric key: this is one deployable signing tokens for itself, so
 * there is no second party that needs a public key, and asymmetric signing would buy
 * nothing but key management. The key is validated at construction, so a missing, short or
 * non-base64 secret stops the application from starting rather than producing tokens a
 * third party could forge.
 *
 * <p>Claims carry the id, the email and the role — no more. Anything mutable (verification
 * state, account status, name) would go stale the moment it changed, because a token cannot
 * be updated once issued; callers that need the live account load it.
 */
@Service
public class JwtServiceImpl implements JwtService {

    /** HS256 requires a key at least as long as its output. */
    private static final int MINIMUM_KEY_BYTES = 32;

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private static final String ERROR_EXPIRED = "ACCESS_TOKEN_EXPIRED";
    private static final String ERROR_INVALID = "ACCESS_TOKEN_INVALID";

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public JwtServiceImpl(JwtProperties properties, Clock clock) {
        this.signingKey = Keys.hmacShaKeyFor(decodeKey(properties.secret()));
        this.issuer = properties.issuer();
        this.accessTokenTtl = properties.accessTokenTtl();
        this.clock = clock;
    }

    @Override
    public String issueAccessToken(User user) {
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(accessTokenTtl)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public AuthenticatedUser parse(String accessToken) {
        Claims claims = claimsOf(accessToken);
        try {
            return new AuthenticatedUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class)));
        } catch (IllegalArgumentException | NullPointerException e) {
            // Correctly signed but not shaped like one of our tokens: an old token format
            // surviving a deployment, or a role that no longer exists.
            throw new AuthenticationFailedException(ERROR_INVALID, "Access token claims are unusable");
        }
    }

    @Override
    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    /**
     * Verifies the signature, the issuer and the expiry in one pass. Expiry is reported
     * under its own code because the client reacts to it differently: an expired token
     * means refresh and retry, any other failure means sign in again.
     */
    private Claims claimsOf(String accessToken) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new AuthenticationFailedException(ERROR_EXPIRED, "Access token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            // Deliberately uninformative: a caller presenting a bad token learns nothing
            // about why it was rejected.
            throw new AuthenticationFailedException(ERROR_INVALID, "Access token is not valid");
        }
    }

    private static byte[] decodeKey(String base64Secret) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64Secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "JWT_SECRET must be base64-encoded. Generate one with: openssl rand -base64 32", e);
        }
        if (key.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must decode to at least %d bytes for HS256, got %d"
                            .formatted(MINIMUM_KEY_BYTES, key.length));
        }
        return key;
    }
}
