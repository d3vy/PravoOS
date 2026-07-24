package com.pravoos.ai.shared.security;

public final class PiiCryptoHolder {

  private static volatile PiiEncryptor encryptor;

  private PiiCryptoHolder() {}

  static void register(PiiEncryptor instance) {
    encryptor = instance;
  }

  public static PiiEncryptor encryptor() {
    PiiEncryptor current = encryptor;
    if (current == null) {
      throw new IllegalStateException("PiiEncryptor is not initialized yet");
    }
    return current;
  }
}
