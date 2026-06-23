package com.pravoos.ai.security;

import com.pravoos.ai.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "this-is-a-very-long-test-secret-key-32b";

    private final JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(SECRET));

    @Test
    void validTokenIsAcceptedAndClaimsExtracted() {
        String token = signedToken(SECRET, "user-123", "LAWYER", new Date(System.currentTimeMillis() + 60_000));

        assertThat(provider.isTokenValid(token)).isTrue();
        assertThat(provider.extractClaims(token).getSubject()).isEqualTo("user-123");
        assertThat(provider.extractClaims(token).get("role", String.class)).isEqualTo("LAWYER");
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = signedToken(SECRET, "user-123", "LAWYER", new Date(System.currentTimeMillis() + 60_000));

        assertThat(provider.isTokenValid(token + "x")).isFalse();
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        String foreignToken = signedToken(
                "another-completely-different-secret-key-32", "user-123", "ADMIN",
                new Date(System.currentTimeMillis() + 60_000));

        assertThat(provider.isTokenValid(foreignToken)).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        String token = signedToken(SECRET, "user-123", "LAWYER", new Date(System.currentTimeMillis() - 1_000));

        assertThat(provider.isTokenValid(token)).isFalse();
    }

    @Test
    void garbageTokenIsRejected() {
        assertThat(provider.isTokenValid("not-a-jwt")).isFalse();
    }

    @Test
    void secretShorterThanThirtyTwoBytesIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties("too-short")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String signedToken(String secret, String subject, String role, Date expiration) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }
}
