package com.pravoos.common.security;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class PiiEncryptorTest {

  private static String randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  private static String legacyTaggedCiphertext(String base64Key, String plaintext) {
    try {
      byte[] iv = new byte[12];
      new SecureRandom().nextBytes(iv);
      SecretKeySpec key = new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES");

      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      byte[] combined = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
      return "pii1:" + Base64.getEncoder().encodeToString(combined);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
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
    assertThrows(PiiCryptoException.class, () -> withoutV1.decrypt(stored));
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

    assertThrows(PiiCryptoException.class, () -> withKey(randomKey()).decrypt(stored));
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

  @Test
  void emptyStringRoundTrips() {
    PiiEncryptor encryptor = withKey(randomKey());

    String stored = encryptor.encrypt("");

    assertNotEquals("", stored);
    assertEquals("", encryptor.decrypt(stored));
  }

  @Test
  void unicodeAndSurrogatePairsRoundTrip() {
    PiiEncryptor encryptor = withKey(randomKey());
    String original = "Şirin İvanova 🙂 — доверенность №42";

    assertEquals(original, encryptor.decrypt(encryptor.encrypt(original)));
  }

  @Test
  void decryptsLegacyTaggedCiphertextUsingExplicitLegacyKey() {
    String legacyKey = randomKey();
    PiiEncryptor encryptor =
        withKeys("v1", Map.of("v1", randomKey(), PiiCryptoProperties.LEGACY_KEY_ID, legacyKey));
    String legacyStored = legacyTaggedCiphertext(legacyKey, "old data");

    assertEquals("old data", encryptor.decrypt(legacyStored));
  }

  @Test
  void decryptsLegacyTaggedCiphertextFallingBackToActiveKeyWhenNoExplicitLegacyKey() {
    String activeKey = randomKey();
    PiiEncryptor encryptor = withKeys("v1", Map.of("v1", activeKey));
    String legacyStored = legacyTaggedCiphertext(activeKey, "old data");

    assertEquals("old data", encryptor.decrypt(legacyStored));
  }

  @Test
  void legacyTaggedCiphertextFailsWithoutAnyKeyConfigured() {
    PiiEncryptor encryptor = withKey(null);
    String legacyStored = legacyTaggedCiphertext(randomKey(), "old data");

    assertThrows(PiiCryptoException.class, () -> encryptor.decrypt(legacyStored));
  }
}
