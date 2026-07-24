package com.pravoos.common.security;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class RsaKeyLoader {

  private RsaKeyLoader() {}

  public static RSAPublicKey loadPublicKey(String key) {
    try {
      byte[] der = decode(key, "PUBLIC KEY");
      return (RSAPublicKey)
          KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    } catch (Exception e) {
      throw new IllegalStateException("Invalid RSA public key", e);
    }
  }

  public static RSAPrivateKey loadPrivateKey(String key) {
    try {
      byte[] der = decode(key, "PRIVATE KEY");
      return (RSAPrivateKey)
          KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (Exception e) {
      throw new IllegalStateException("Invalid RSA private key", e);
    }
  }

  private static byte[] decode(String key, String pemLabel) {
    if (key == null || key.isBlank()) {
      throw new IllegalStateException("RSA key is not configured");
    }
    String normalized =
        key.replace("-----BEGIN " + pemLabel + "-----", "")
            .replace("-----END " + pemLabel + "-----", "")
            .replaceAll("\\s", "");
    return Base64.getDecoder().decode(normalized);
  }
}
