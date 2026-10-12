package com.settribe.leave.security;

import com.settribe.leave.entity.Role;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Utility class for generating, parsing, and validating JSON Web Tokens (JWT) using JJWT 0.12+.
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    /**
     * Initializes the HMAC-SHA signing key from the application configuration.
     * Enforces a minimum 256-bit (32 character) secret requirement for cryptographic security.
     */
    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration-ms}") long expirationMs) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMs = expirationMs;
    }

    /**
     * Generates a digitally signed JWT token containing the user's email as the subject,
     * the role as a custom claim, issue timestamp, and expiration time.
     * 
     * @param email User email (token subject).
     * @param role  User role (EMPLOYEE or ADMIN).
     * @return Compact serialized JWT string.
     */
    public String generateToken(String email, Role role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    /**
     * Extracts and returns the user email (subject) from a signed JWT token.
     * 
     * @param token Compact JWT string.
     * @return Email address stored in the subject claim.
     */
    public String extractEmail(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().getSubject();
    }

    /**
     * Validates that the token's cryptographic signature matches and that it has not expired.
     * 
     * @param token Compact JWT string.
     * @return true if token is structurally valid, signed with our key, and not expired; false otherwise.
     */
    public boolean isValid(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}

