package com.pravoos.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.security.interfaces.RSAPublicKey;

public class JwtVerifier {

  private final RSAPublicKey publicKey;

  public JwtVerifier(String publicKeyPem) {
    this.publicKey = RsaKeyLoader.loadPublicKey(publicKeyPem);
  }

  public Claims extractClaims(String token) {
    return Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token).getPayload();
  }

  public boolean isValid(String token) {
    try {
      extractClaims(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }
}
