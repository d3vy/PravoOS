package com.pravoos.ai.security;

import com.pravoos.ai.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final KeyPair FOREIGN_KEY_PAIR = generateKeyPair();

    private final JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(publicKeyBase64(KEY_PAIR)));

    @Test
    void validTokenIsAcceptedAndClaimsExtracted() {
        String token = signedToken(KEY_PAIR, "user-123", "LAWYER", new Date(System.currentTimeMillis() + 60_000));

        assertThat(provider.isTokenValid(token)).isTrue();
        assertThat(provider.extractClaims(token).getSubject()).isEqualTo("user-123");
        assertThat(provider.extractClaims(token).get("role", String.class)).isEqualTo("LAWYER");
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = signedToken(KEY_PAIR, "user-123", "LAWYER", new Date(System.currentTimeMillis() + 60_000));

        assertThat(provider.isTokenValid(token + "x")).isFalse();
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        String foreignToken = signedToken(FOREIGN_KEY_PAIR, "user-123", "ADMIN",
                new Date(System.currentTimeMillis() + 60_000));

        assertThat(provider.isTokenValid(foreignToken)).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        String token = signedToken(KEY_PAIR, "user-123", "LAWYER", new Date(System.currentTimeMillis() - 1_000));

        assertThat(provider.isTokenValid(token)).isFalse();
    }

    @Test
    void garbageTokenIsRejected() {
        assertThat(provider.isTokenValid("not-a-jwt")).isFalse();
    }

    @Test
    void blankPublicKeyIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties("")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String signedToken(KeyPair keyPair, String subject, String role, Date expiration) {
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .expiration(expiration)
                .signWith((RSAPrivateKey) keyPair.getPrivate())
                .compact();
    }

    private static String publicKeyBase64(KeyPair keyPair) {
        return Base64.getEncoder().encodeToString(((RSAPublicKey) keyPair.getPublic()).getEncoded());
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
