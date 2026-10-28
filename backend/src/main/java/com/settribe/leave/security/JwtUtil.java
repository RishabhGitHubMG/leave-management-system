package com.settribe.leave.security;

import com.settribe.leave.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Generates and verifies HS256-signed JSON Web Tokens (JJWT 0.12+).
 */
@Component
public class JwtUtil {

    private static final String ALGORITHM = Jwts.SIG.HS256.getId();

    private final SecretKey key;
    private final String issuer;
    private final long expirationMs;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.issuer}") String issuer,
                   @Value("${app.jwt.expiration-ms}") long expirationMs) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secret.isBlank() || bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 UTF-8 bytes.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.issuer = issuer;
        this.expirationMs = expirationMs;
    }

    /**
     * Issues a signed token whose subject is the immutable user id.
     *
     * @param userId Database id of the authenticated user.
     * @param role   Role at issue time; informational only, authorization reloads roles from the database.
     * @return Compact serialized JWT string.
     */
    public String generateToken(long userId, Role role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(Long.toString(userId))
                .issuer(issuer)
                .id(UUID.randomUUID().toString())
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verifies a token's signature, algorithm, issuer, and expiration, then returns its user id.
     *
     * @param token Compact JWT string.
     * @return User id stored in the subject claim.
     * @throws io.jsonwebtoken.JwtException if the token is invalid in any way.
     */
    public long extractUserId(String token) {
        Claims claims = parse(token);
        try {
            return Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new MalformedJwtException("Token subject is not a user id");
        }
    }

    private Claims parse(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);
        if (!ALGORITHM.equals(jws.getHeader().getAlgorithm())) {
            throw new UnsupportedJwtException("Unexpected JWT signing algorithm");
        }
        return jws.getPayload();
    }
}
