package com.eduvision.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PBKDF2WithHmacSHA256 password hashing.
 *
 * <p>Spring Security is deliberately NOT a dependency yet (auth is a later task), so this is a
 * small self-contained implementation. Plain-text passwords are never stored.
 *
 * <p>Encoded form (also the database value):
 * <pre>pbkdf2-sha256$&lt;iterations&gt;$&lt;base64 salt&gt;$&lt;base64 hash&gt;</pre>
 *
 * <p>Each user gets its own 16-byte cryptographically random salt.
 */
public final class PasswordUtil {

    /** Iteration count - comfortably above the 60000 required minimum. */
    public static final int DEFAULT_ITERATIONS = 120_000;

    public static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final String PREFIX = "pbkdf2-sha256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getEncoder();
    private static final Base64.Decoder B64D = Base64.getDecoder();

    private PasswordUtil() {
    }

    /**
     * Hashes a plain-text password with a brand-new random salt.
     *
     * @return the encoded {@code pbkdf2-sha256$iterations$salt$hash} string
     */
    public static String hash(String plainTextPassword) {
        return hash(plainTextPassword, DEFAULT_ITERATIONS);
    }

    /**
     * Hashes a plain-text password with a brand-new random salt and an explicit iteration count.
     */
    public static String hash(String plainTextPassword, int iterations) {
        if (plainTextPassword == null || plainTextPassword.isEmpty()) {
            throw new IllegalArgumentException("Password must not be null or empty");
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] dk = derive(plainTextPassword, salt, iterations);
        return PREFIX + "$" + iterations + "$" + B64.encodeToString(salt) + "$" + B64.encodeToString(dk);
    }

    /**
     * Constant-time verification of a plain-text password against an encoded hash.
     */
    public static boolean verify(String plainTextPassword, String encoded) {
        if (plainTextPassword == null || encoded == null || encoded.isBlank()) {
            return false;
        }
        String[] parts = encoded.split("\\$");
        // pbkdf2-sha256, iterations, salt, hash
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        int iterations;
        byte[] salt;
        byte[] expected;
        try {
            iterations = Integer.parseInt(parts[1]);
            salt = B64D.decode(parts[2]);
            expected = B64D.decode(parts[3]);
        } catch (RuntimeException ex) {
            return false;
        }
        byte[] actual = derive(plainTextPassword, salt, iterations);
        return MessageDigest.isEqual(expected, actual);
    }

    /**
     * True when the value looks like an encoded PBKDF2 hash (guards against plain text leaking
     * into the users table).
     */
    public static boolean isEncodedHash(String value) {
        return value != null && value.startsWith(PREFIX + "$");
    }

    private static byte[] derive(String plainTextPassword, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(plainTextPassword.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("PBKDF2 unavailable on this JVM", ex);
        } finally {
            spec.clearPassword();
        }
    }

    /** Convenience overload accepting bytes, for callers that already encode. */
    static byte[] derive(byte[] passwordBytes, byte[] salt, int iterations) {
        return derive(new String(passwordBytes, StandardCharsets.UTF_8), salt, iterations);
    }
}