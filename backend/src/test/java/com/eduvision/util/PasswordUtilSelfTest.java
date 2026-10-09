package com.eduvision.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Self-test proving passwords are never stored plain text and that verification works.
 * Run with:  mvn test -Dtest=PasswordUtilSelfTest
 */
class PasswordUtilSelfTest {

    @Test
    @DisplayName("hash != plain text, verify() accepts the right password and rejects the wrong one")
    void hashIsNotPlainTextAndVerifyWorks() {
        String plain = "Teacher@123";

        String encoded = PasswordUtil.hash(plain);

        // 1. the stored value must NOT contain the plain text
        assertNotEquals(plain, encoded, "hash must not equal the plain-text password");
        assertFalse(encoded.contains(plain), "encoded hash must not contain the plain-text password");

        // 2. it must be the documented PBKDF2 shape
        assertTrue(PasswordUtil.isEncodedHash(encoded), "must start with pbkdf2-sha256$");
        assertEquals(4, encoded.split("\\$").length, "must be iterations$salt$hash");
        assertTrue(encoded.startsWith("pbkdf2-sha256$" + PasswordUtil.DEFAULT_ITERATIONS + "$"),
                "must record the iteration count (>= 60000 required)");

        // 3. verify() must accept the correct password ...
        assertTrue(PasswordUtil.verify(plain, encoded), "correct password must verify");
        // ... and reject a wrong one
        assertFalse(PasswordUtil.verify("Teacher@1234", encoded), "wrong password must NOT verify");
        assertFalse(PasswordUtil.verify("", encoded), "empty password must NOT verify");
        assertFalse(PasswordUtil.verify(null, encoded), "null password must NOT verify");
    }

    @Test
    @DisplayName("per-user random salt means the same password never produces the same hash twice")
    void saltIsRandomPerHash() {
        String plain = "Student@123";
        String a = PasswordUtil.hash(plain);
        String b = PasswordUtil.hash(plain);

        assertNotEquals(a, b, "two hashes of the same password must differ (random salt)");
        assertTrue(PasswordUtil.verify(plain, a));
        assertTrue(PasswordUtil.verify(plain, b));
    }

    @Test
    @DisplayName("malformed or blank stored values are rejected safely, never throwing")
    void malformedInputIsRejected() {
        assertFalse(PasswordUtil.verify("Teacher@123", null));
        assertFalse(PasswordUtil.verify("Teacher@123", ""));
        assertFalse(PasswordUtil.verify("Teacher@123", "plain-text-password"));
        assertFalse(PasswordUtil.verify("Teacher@123", "pbkdf2-sha256$notanumber$c2FsdA==$aGFzaA=="));
        assertFalse(PasswordUtil.verify("Teacher@123", "bcrypt$10$abc$def"));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hash(null));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hash(""));
    }

    @Test
    @DisplayName("iteration count is well above the required 60000 minimum")
    void iterationCountMeetsMinimum() {
        assertTrue(PasswordUtil.DEFAULT_ITERATIONS >= 60_000,
                "PBKDF2 must use >= 60000 iterations, got " + PasswordUtil.DEFAULT_ITERATIONS);
    }
}