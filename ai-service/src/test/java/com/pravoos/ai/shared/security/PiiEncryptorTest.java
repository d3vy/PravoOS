package com.pravoos.ai.shared.security;

import static org.junit.jupiter.api.Assertions.*;

import com.pravoos.ai.shared.config.PiiCryptoProperties;
import com.pravoos.ai.shared.exception.DocumentProcessingException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PiiEncryptorTest {

  private static String randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  private PiiEncryptor withKey(String base64Key) {
    return new PiiEncryptor(new PiiCryptoProperties(false, null, null, base64Key));
  }

  private PiiEncryptor withKeys(String activeKeyId, Map<String, String> keys) {
    return new PiiEncryptor(new PiiCryptoProperties(false, activeKeyId, keys, null));
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
  void tagsCiphertextWithActiveKeyId() {
    PiiEncryptor encryptor = withKeys("v1", Map.of("v1", randomKey()));

    String stored = encryptor.encrypt("secret");

    assertTrue(stored.startsWith("pii2:v1:"));
    assertTrue(encryptor.isEncryptedWithActiveKey(stored));
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
  void rotatedKeyStillDecryptsDataEncryptedWithOldKey() {
    String oldKey = randomKey();
    String newKey = randomKey();

    PiiEncryptor before = withKeys("v1", Map.of("v1", oldKey));
    String stored = before.encrypt("Пётр Петров");

    PiiEncryptor after = withKeys("v2", Map.of("v1", oldKey, "v2", newKey));
    assertEquals("Пётр Петров", after.decrypt(stored));
    assertFalse(after.isEncryptedWithActiveKey(stored));
    assertTrue(after.isEncryptedWithActiveKey(after.encrypt("Пётр Петров")));
  }

  @Test
  void failsWhenKeyForStoredKeyIdIsMissing() {
    String stored = withKeys("v1", Map.of("v1", randomKey())).encrypt("secret");

    PiiEncryptor withoutV1 = withKeys("v2", Map.of("v2", randomKey()));
    assertThrows(DocumentProcessingException.class, () -> withoutV1.decrypt(stored));
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
    assertFalse(encryptor.isEncryptionEnabled());
  }

  @Test
  void failsFastWhenRequiredButNoKeyConfigured() {
    assertThrows(
        IllegalStateException.class,
        () -> new PiiEncryptor(new PiiCryptoProperties(true, null, null, null)));
  }

  @Test
  void failsWhenActiveKeyIdHasNoMatchingKey() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new PiiEncryptor(
                new PiiCryptoProperties(false, "v3", Map.of("v1", randomKey()), null)));
  }

  @Test
  void rejectsKeyOfWrongLength() {
    String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
    assertThrows(IllegalStateException.class, () -> withKey(shortKey));
  }
}
