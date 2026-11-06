package com.chamabosal.global.security.jwt;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtProvider {

    public static final long EXPIRES_IN_SECONDS = 3600;

    private final SecretKey secretKey;
    private final JwtParser jwtParser;
    private final Clock clock;

    public JwtProvider(@Value("${jwt.secret}") String secret, Clock clock) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.jwtParser = Jwts.parser()
                .verifyWith(secretKey)
                .clock(() -> Date.from(clock.instant()))
                .build();
        this.clock = clock;
    }

    public String createAccessToken(Long userId) {
        return createToken(userId, EXPIRES_IN_SECONDS);
    }

    public String createToken(Long userId, long expiresInSeconds) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expiresInSeconds)))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public Optional<Long> parseUserId(String token) {
        try {
            String subject = jwtParser.parseSignedClaims(token).getPayload().getSubject();
            return Optional.of(Long.valueOf(subject));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
