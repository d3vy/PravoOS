package com.pravoos.ai.shared.security;

import static org.junit.jupiter.api.Assertions.*;

import com.pravoos.ai.shared.config.PiiCryptoProperties;
import com.pravoos.ai.shared.exception.DocumentProcessingException;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PiiEncryptorTest {

  private static String randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  private PiiEncryptor withKey(String base64Key) {
    return new PiiEncryptor(new PiiCryptoProperties(base64Key));
  }

  @Test
  void encryptsThenDecryptsBackToOriginal() {
    PiiEncryptor encryptor = withKey(randomKey());
    String original = "Иванов Иван Иванович";

    String stored = encryptor.encrypt(original);

    assertNotEquals(original, stored);
    assertFalse(stored.contains(original));
    assertEquals(original, encryptor.decrypt(stored));
  }

  @Test
  void sameValueEncryptsToDifferentCiphertext() {
    PiiEncryptor encryptor = withKey(randomKey());

    assertNotEquals(
        encryptor.encrypt("client@example.com"), encryptor.encrypt("client@example.com"));
  }

  @Test
  void decryptsLegacyPlaintextWithoutMarker() {
    PiiEncryptor encryptor = withKey(randomKey());

    assertEquals("+79991234567", encryptor.decrypt("+79991234567"));
  }

  @Test
  void passesThroughNull() {
    PiiEncryptor encryptor = withKey(randomKey());

    assertNull(encryptor.encrypt(null));
    assertNull(encryptor.decrypt(null));
  }

  @Test
  void wrongKeyFailsToDecrypt() {
    String stored = withKey(randomKey()).encrypt("secret");

    assertThrows(DocumentProcessingException.class, () -> withKey(randomKey()).decrypt(stored));
  }

  @Test
  void withoutKeyStoresAndReadsPlaintext() {
    PiiEncryptor encryptor = withKey(null);

    String stored = encryptor.encrypt("no-key-mode");

    assertEquals("no-key-mode", stored);
    assertEquals("no-key-mode", encryptor.decrypt(stored));
  }

  @Test
  void rejectsKeyOfWrongLength() {
    String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
    assertThrows(IllegalStateException.class, () -> withKey(shortKey));
  }
}
