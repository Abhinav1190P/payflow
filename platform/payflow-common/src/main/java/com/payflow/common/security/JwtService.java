package com.payflow.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

// Concept: JWT. A signed token carries the user id and role, so every service can authenticate a request on
// its own without calling auth-service. HMAC-SHA256: anyone can read the claims, nobody can change them.
@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final Duration ttl;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.issuer();
        this.ttl = properties.ttl();
        this.clock = clock;
    }

    public String issue(UUID userId, String email, Role role) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public AuthenticatedUser parse(String token) throws InvalidTokenException {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    claims.get("email", String.class),
                    Role.valueOf(claims.get("role", String.class)));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw new InvalidTokenException("invalid or expired token", e);
        }
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }
}
