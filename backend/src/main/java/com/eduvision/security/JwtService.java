package com.eduvision.security;

import com.eduvision.domain.Role;
import com.eduvision.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/** Issues and validates the HS256 JWTs used by the AR EduVision API. */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(@Value("${eduvision.jwt.secret}") String secret,
                      @Value("${eduvision.jwt.ttl-hours:12}") long ttlHours) {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            throw new IllegalStateException("eduvision.jwt.secret must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(raw);
        this.ttl = Duration.ofHours(ttlHours);
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.email)
                .claim("uid", user.id)
                .claim("role", user.role.name())
                .claim("name", user.name)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    /** @return the subject (email) or {@code null} when the token is invalid/expired */
    public String extractUsername(String token) {
        try {
            Claims claims = parse(token);
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean isValid(String token, User user) {
        try {
            Claims claims = parse(token);
            return user.getEmail().equalsIgnoreCase(claims.getSubject())
                    && claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}