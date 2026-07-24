package com.pravoos.ai.shared.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Sha256 {

  private static final char[] HEX = "0123456789abcdef".toCharArray();

  private Sha256() {}

  public static String hex(byte[] content) {
    MessageDigest digest = newDigest();
    byte[] hash = digest.digest(content);
    return toHex(hash);
  }

  public static String hex(String value) {
    return hex(value.getBytes(StandardCharsets.UTF_8));
  }

  private static MessageDigest newDigest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm is not available", e);
    }
  }

  private static String toHex(byte[] bytes) {
    char[] out = new char[bytes.length * 2];
    for (int i = 0; i < bytes.length; i++) {
      int value = bytes[i] & 0xFF;
      out[i * 2] = HEX[value >>> 4];
      out[i * 2 + 1] = HEX[value & 0x0F];
    }
    return new String(out);
  }
}
