package com.pravoos.user.security;

import com.pravoos.user.config.JwtProperties;
import com.pravoos.user.model.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey secretKey;
    private final long accessExpirationMs;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        byte[] secretBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT secret must be at least " + MIN_SECRET_BYTES + " bytes for HS256");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
        this.accessExpirationMs = jwtProperties.accessExpirationMs();
    }

    public String generateToken(UUID userId, String email, UserRole role) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpirationMs))
                .signWith(secretKey)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
