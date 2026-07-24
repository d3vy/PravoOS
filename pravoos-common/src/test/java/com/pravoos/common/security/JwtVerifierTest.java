package com.pravoos.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;

class JwtVerifierTest {

  private static final KeyPair KEY_PAIR = generateKeyPair();
  private static final KeyPair FOREIGN_KEY_PAIR = generateKeyPair();

  private final JwtVerifier verifier = new JwtVerifier(publicKeyBase64(KEY_PAIR));

  @Test
  void validTokenIsAcceptedAndClaimsExtracted() {
    String token =
        signedToken(KEY_PAIR, "user-123", "LAWYER", new Date(System.currentTimeMillis() + 60_000));

    assertThat(verifier.isValid(token)).isTrue();
    assertThat(verifier.extractClaims(token).getSubject()).isEqualTo("user-123");
    assertThat(verifier.extractClaims(token).get("role", String.class)).isEqualTo("LAWYER");
  }

  @Test
  void tamperedTokenIsRejected() {
    String token =
        signedToken(KEY_PAIR, "user-123", "LAWYER", new Date(System.currentTimeMillis() + 60_000));

    assertThat(verifier.isValid(token + "x")).isFalse();
  }

  @Test
  void tokenSignedWithDifferentKeyIsRejected() {
    String foreignToken =
        signedToken(
            FOREIGN_KEY_PAIR, "user-123", "ADMIN", new Date(System.currentTimeMillis() + 60_000));

    assertThat(verifier.isValid(foreignToken)).isFalse();
  }

  @Test
  void expiredTokenIsRejected() {
    String token =
        signedToken(KEY_PAIR, "user-123", "LAWYER", new Date(System.currentTimeMillis() - 1_000));

    assertThat(verifier.isValid(token)).isFalse();
  }

  @Test
  void garbageTokenIsRejected() {
    assertThat(verifier.isValid("not-a-jwt")).isFalse();
  }

  @Test
  void blankPublicKeyIsRejectedAtConstruction() {
    assertThatThrownBy(() -> new JwtVerifier("")).isInstanceOf(IllegalStateException.class);
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
