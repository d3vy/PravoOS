package com.pravoos.ai.shared.security;

import com.pravoos.ai.shared.config.PiiCryptoProperties;
import com.pravoos.ai.shared.exception.DocumentProcessingException;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PiiEncryptor {

  private static final Logger log = LoggerFactory.getLogger(PiiEncryptor.class);

  private static final String LEGACY_MARKER = "pii1:";
  private static final String MARKER_PREFIX = "pii2:";
  private static final char KEY_ID_SEPARATOR = ':';
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH = 12;
  private static final int TAG_LENGTH_BITS = 128;
  private static final int KEY_LENGTH_BYTES = 32;

  private final Map<String, SecretKey> keysById;
  private final String activeKeyId;
  private final SecureRandom secureRandom = new SecureRandom();

  public PiiEncryptor(PiiCryptoProperties properties) {
    this.keysById = loadKeys(properties.resolvedKeys());
    String resolvedActive = properties.resolvedActiveKeyId();

    if (resolvedActive == null) {
      if (properties.required()) {
        throw new IllegalStateException(
            "PII encryption is required (pii.crypto.required=true) but no key is configured");
      }
      this.activeKeyId = null;
      log.warn(
          "PII encryption disabled: no key configured. "
              + "Personal data columns are written in plaintext.");
      return;
    }
    if (!keysById.containsKey(resolvedActive)) {
      throw new IllegalStateException(
          "pii.crypto.active-key-id '"
              + resolvedActive
              + "' has no matching entry in pii.crypto.keys");
    }
    this.activeKeyId = resolvedActive;
    log.info(
        "PII encryption enabled with key '{}' ({} key(s) available for decryption)",
        activeKeyId,
        keysById.size());
  }

  @PostConstruct
  void register() {
    PiiCryptoHolder.register(this);
  }

  public boolean isEncryptionEnabled() {
    return activeKeyId != null;
  }

  public boolean isEncryptedWithActiveKey(String stored) {
    return activeKeyId != null
        && stored != null
        && stored.startsWith(MARKER_PREFIX + activeKeyId + KEY_ID_SEPARATOR);
  }

  public String encrypt(String plaintext) {
    if (plaintext == null || activeKeyId == null) {
      return plaintext;
    }
    return encryptWith(activeKeyId, keysById.get(activeKeyId), plaintext);
  }

  public String decrypt(String stored) {
    if (stored == null) {
      return null;
    }
    if (stored.startsWith(MARKER_PREFIX)) {
      return decryptTagged(stored);
    }
    if (stored.startsWith(LEGACY_MARKER)) {
      return decryptLegacy(stored);
    }
    return stored;
  }

  private String encryptWith(String keyId, SecretKey key, String plaintext) {
    try {
      byte[] iv = new byte[IV_LENGTH];
      secureRandom.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      byte[] combined = new byte[IV_LENGTH + ciphertext.length];
      System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
      System.arraycopy(ciphertext, 0, combined, IV_LENGTH, ciphertext.length);
      return MARKER_PREFIX
          + keyId
          + KEY_ID_SEPARATOR
          + Base64.getEncoder().encodeToString(combined);
    } catch (GeneralSecurityException e) {
      throw new DocumentProcessingException("Failed to encrypt personal data: " + e.getMessage());
    }
  }

  private String decryptTagged(String stored) {
    int keyIdStart = MARKER_PREFIX.length();
    int separator = stored.indexOf(KEY_ID_SEPARATOR, keyIdStart);
    if (separator < 0) {
      throw new DocumentProcessingException("Malformed encrypted personal data: missing key id");
    }
    String keyId = stored.substring(keyIdStart, separator);
    SecretKey key = keysById.get(keyId);
    if (key == null) {
      throw new DocumentProcessingException(
          "No PII key configured for id '" + keyId + "' — cannot decrypt");
    }
    return decryptPayload(key, stored.substring(separator + 1));
  }

  private String decryptLegacy(String stored) {
    SecretKey key = legacyKey();
    if (key == null) {
      throw new DocumentProcessingException(
          "Legacy encrypted personal data found but no key configured to decrypt it");
    }
    return decryptPayload(key, stored.substring(LEGACY_MARKER.length()));
  }

  private SecretKey legacyKey() {
    SecretKey legacy = keysById.get(PiiCryptoProperties.LEGACY_KEY_ID);
    if (legacy != null) {
      return legacy;
    }
    return activeKeyId == null ? null : keysById.get(activeKeyId);
  }

  private String decryptPayload(SecretKey key, String base64Payload) {
    try {
      byte[] combined = Base64.getDecoder().decode(base64Payload);
      byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
      byte[] ciphertext = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new DocumentProcessingException("Failed to decrypt personal data: " + e.getMessage());
    }
  }

  private Map<String, SecretKey> loadKeys(Map<String, String> rawKeys) {
    Map<String, SecretKey> loaded = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : rawKeys.entrySet()) {
      String keyId = entry.getKey();
      if (keyId.indexOf(KEY_ID_SEPARATOR) >= 0) {
        throw new IllegalStateException("pii.crypto key id must not contain ':': " + keyId);
      }
      loaded.put(keyId, loadKey(keyId, entry.getValue()));
    }
    return loaded;
  }

  private SecretKey loadKey(String keyId, String base64Key) {
    if (base64Key == null || base64Key.isBlank()) {
      throw new IllegalStateException("pii.crypto key '" + keyId + "' is empty");
    }
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(base64Key.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("pii.crypto key '" + keyId + "' must be valid Base64", e);
    }
    if (decoded.length != KEY_LENGTH_BYTES) {
      throw new IllegalStateException(
          "pii.crypto key '"
              + keyId
              + "' must decode to "
              + KEY_LENGTH_BYTES
              + " bytes (AES-256), got "
              + decoded.length);
    }
    return new SecretKeySpec(decoded, "AES");
  }
}
