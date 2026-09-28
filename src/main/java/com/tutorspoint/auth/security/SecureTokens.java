package com.tutorspoint.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Mints the secrets that are mailed or texted to a user, and digests them for storage.
 *
 * <p>Two kinds of secret, deliberately treated differently:
 *
 * <ul>
 *   <li>A <strong>link token</strong> is 256 bits of randomness. It is stored as a
 *       deterministic SHA-256 digest, which is what lets the row be found from the token
 *       alone; an unsalted digest is safe here only because the input is far too large to
 *       search.</li>
 *   <li>A <strong>numeric code</strong> is six digits — a million possibilities, trivially
 *       searchable. It is never looked up by digest; it is found by user and compared under
 *       a salted, deliberately slow hash, with an attempt ceiling doing the real work.
 *       Hashing alone would not protect it.</li>
 * </ul>
 *
 * <p>A static utility rather than a bean: these are pure functions over a
 * {@link SecureRandom}, with no configuration and nothing worth substituting in a test.
 */
public final class SecureTokens {

    /** 32 bytes = 256 bits, the same strength as the signing key. */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private SecureTokens() {
    }

    /** A new link token, URL-safe so it drops straight into an emailed link. */
    public static String newLinkToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    /**
     * A uniformly random code of the given length, leading zeros preserved — 004271 is as
     * valid a code as any other, so the value is built digit by digit rather than by
     * formatting a number.
     */
    public static String newNumericCode(int digits) {
        if (digits < 1) {
            throw new IllegalArgumentException("digits must be positive");
        }
        StringBuilder code = new StringBuilder(digits);
        for (int i = 0; i < digits; i++) {
            code.append(RANDOM.nextInt(10));
        }
        return code.toString();
    }

    /** Hex-encoded SHA-256, the form stored in the token_hash columns. */
    public static String sha256Hex(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by every JDK; its absence is not a runtime condition.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
