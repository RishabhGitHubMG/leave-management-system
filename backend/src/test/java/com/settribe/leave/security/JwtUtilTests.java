package com.settribe.leave.security;

import com.settribe.leave.entity.Role;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTests {

    private static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes";
    private static final String ISSUER = "settribe-leave-management";
    private static final long ONE_HOUR_MS = 3_600_000L;

    @Test
    void roundTripReturnsSubjectUserId() {
        JwtUtil jwt = new JwtUtil(SECRET, ISSUER, ONE_HOUR_MS);

        assertEquals(42L, jwt.extractUserId(jwt.generateToken(42L, Role.ADMIN)));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtUtil issuer = new JwtUtil(SECRET, ISSUER, -60_000L);
        JwtUtil verifier = new JwtUtil(SECRET, ISSUER, ONE_HOUR_MS);

        String expired = issuer.generateToken(7L, Role.EMPLOYEE);

        assertThrows(ExpiredJwtException.class, () -> verifier.extractUserId(expired));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String foreign = new JwtUtil("another-unit-test-secret-of-32-bytes!", ISSUER, ONE_HOUR_MS)
                .generateToken(7L, Role.EMPLOYEE);

        assertThrows(JwtException.class,
                () -> new JwtUtil(SECRET, ISSUER, ONE_HOUR_MS).extractUserId(foreign));
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String foreign = new JwtUtil(SECRET, "some-other-issuer", ONE_HOUR_MS)
                .generateToken(7L, Role.EMPLOYEE);

        assertThrows(JwtException.class,
                () -> new JwtUtil(SECRET, ISSUER, ONE_HOUR_MS).extractUserId(foreign));
    }

    @Test
    void secretShorterThan32BytesFailsStartup() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil("too-short", ISSUER, ONE_HOUR_MS));
    }

    @Test
    void blankSecretFailsStartup() {
        assertThrows(IllegalStateException.class,
                () -> new JwtUtil("                                  ", ISSUER, ONE_HOUR_MS));
    }
}
