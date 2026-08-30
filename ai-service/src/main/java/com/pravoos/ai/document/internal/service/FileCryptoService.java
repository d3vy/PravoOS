package com.pravoos.ai.document.internal.service;

import com.pravoos.ai.shared.config.FileCryptoProperties;
import com.pravoos.ai.shared.exception.DocumentProcessingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.stereotype.Service;

/**
 * Container format on disk:
 *
 * <ul>
 *   <li>{@code POS2} — {@code magic | keyIdLength (1 byte) | keyId | iv (12) | ciphertext+tag}. The
 *       whole header is fed to GCM as AAD, so a tampered key id fails authentication.
 *   <li>{@code POS1} — legacy single-key layout {@code magic | iv (12) | ciphertext+tag}, readable
 *       with the key configured under {@code document.crypto.key}.
 *   <li>anything else — plaintext written before encryption was enabled.
 * </ul>
 *
 * Rotation: add the new key to {@code document.crypto.keys} and point {@code
 * document.crypto.active-key-id} at it. Old keys stay listed so previously stored files remain
 * readable; a key may be dropped only once no file references its id.
 */
@Service
public class FileCryptoService {

  private static final Logger log = LoggerFactory.getLogger(FileCryptoService.class);

  private static final byte[] LEGACY_MAGIC = {0x50, 0x4F, 0x53, 0x31};
  private static final byte[] MAGIC = {0x50, 0x4F, 0x53, 0x32};
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH = 12;
  private static final int TAG_LENGTH_BITS = 128;
  private static final int KEY_LENGTH_BYTES = 32;
  private static final int KEY_ID_LENGTH_FIELD = 1;
  private static final int MAX_KEY_ID_BYTES = 255;
  private static final int LEGACY_PREFIX_LENGTH = LEGACY_MAGIC.length + IV_LENGTH;

  private final Map<String, SecretKey> keysById;
  private final String activeKeyId;
  private final SecureRandom secureRandom = new SecureRandom();

  public FileCryptoService(FileCryptoProperties properties) {
    this.keysById = loadKeys(properties.resolvedKeys());
    String resolvedActive = properties.resolvedActiveKeyId();

    if (keysById.isEmpty()) {
      this.activeKeyId = null;
      log.warn(
          "File encryption disabled: no document.crypto key is configured. "
              + "New files are written in plaintext.");
      return;
    }
    if (resolvedActive == null) {
      throw new IllegalStateException(
          "document.crypto.active-key-id must be set when more than one key is configured");
    }
    if (!keysById.containsKey(resolvedActive)) {
      throw new IllegalStateException(
          "document.crypto.active-key-id '"
              + resolvedActive
              + "' has no matching entry in document.crypto.keys");
    }
    this.activeKeyId = resolvedActive;
    log.info(
        "File encryption enabled with key '{}' ({} key(s) available for decryption)",
        activeKeyId,
        keysById.size());
  }

  public void encryptToFile(byte[] plaintext, Path target) {
    try {
      byte[] payload = activeKeyId == null ? plaintext : encrypt(plaintext);
      Files.write(target, payload);
    } catch (GeneralSecurityException e) {
      throw new DocumentProcessingException("Failed to encrypt file: " + e.getMessage());
    } catch (java.io.IOException e) {
      throw new DocumentProcessingException("Failed to store file: " + e.getMessage());
    }
  }

  public byte[] decryptFile(Path source) {
    try {
      byte[] payload = Files.readAllBytes(source);
      if (startsWith(payload, MAGIC)) {
        return decrypt(payload);
      }
      if (startsWith(payload, LEGACY_MAGIC) && payload.length >= LEGACY_PREFIX_LENGTH) {
        return decryptLegacy(payload);
      }
      return payload;
    } catch (GeneralSecurityException e) {
      throw new DocumentProcessingException("Failed to decrypt file: " + e.getMessage());
    } catch (java.io.IOException e) {
      throw new DocumentProcessingException("Failed to read file: " + e.getMessage());
    }
  }

  private byte[] encrypt(byte[] plaintext) throws GeneralSecurityException {
    byte[] keyIdBytes = activeKeyId.getBytes(StandardCharsets.UTF_8);
    byte[] header = new byte[MAGIC.length + KEY_ID_LENGTH_FIELD + keyIdBytes.length];
    System.arraycopy(MAGIC, 0, header, 0, MAGIC.length);
    header[MAGIC.length] = (byte) keyIdBytes.length;
    System.arraycopy(keyIdBytes, 0, header, MAGIC.length + KEY_ID_LENGTH_FIELD, keyIdBytes.length);

    byte[] iv = new byte[IV_LENGTH];
    secureRandom.nextBytes(iv);

    Cipher cipher = Cipher.getInstance(TRANSFORMATION);
    cipher.init(
        Cipher.ENCRYPT_MODE, keysById.get(activeKeyId), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
    cipher.updateAAD(header);

    int prefixLength = header.length + IV_LENGTH;
    byte[] result = new byte[prefixLength + cipher.getOutputSize(plaintext.length)];
    System.arraycopy(header, 0, result, 0, header.length);
    System.arraycopy(iv, 0, result, header.length, IV_LENGTH);
    cipher.doFinal(plaintext, 0, plaintext.length, result, prefixLength);
    return result;
  }

  private byte[] decrypt(byte[] payload) throws GeneralSecurityException {
    if (payload.length < MAGIC.length + KEY_ID_LENGTH_FIELD) {
      throw new DocumentProcessingException("Malformed encrypted file: truncated header");
    }
    int keyIdLength = payload[MAGIC.length] & 0xFF;
    int headerLength = MAGIC.length + KEY_ID_LENGTH_FIELD + keyIdLength;
    if (keyIdLength == 0 || payload.length < headerLength + IV_LENGTH) {
      throw new DocumentProcessingException("Malformed encrypted file: truncated key id");
    }

    String keyId =
        new String(
            payload, MAGIC.length + KEY_ID_LENGTH_FIELD, keyIdLength, StandardCharsets.UTF_8);
    SecretKey key = keysById.get(keyId);
    if (key == null) {
      throw new DocumentProcessingException(
          "No document.crypto key configured for id '" + keyId + "' — cannot decrypt");
    }

    Cipher cipher = Cipher.getInstance(TRANSFORMATION);
    cipher.init(
        Cipher.DECRYPT_MODE,
        key,
        new GCMParameterSpec(TAG_LENGTH_BITS, payload, headerLength, IV_LENGTH));
    cipher.updateAAD(Arrays.copyOfRange(payload, 0, headerLength));

    int prefixLength = headerLength + IV_LENGTH;
    return cipher.doFinal(payload, prefixLength, payload.length - prefixLength);
  }

  private byte[] decryptLegacy(byte[] payload) throws GeneralSecurityException {
    SecretKey key = keysById.get(FileCryptoProperties.LEGACY_KEY_ID);
    if (key == null && activeKeyId != null && keysById.size() == 1) {
      key = keysById.get(activeKeyId);
    }
    if (key == null) {
      throw new DocumentProcessingException(
          "Legacy encrypted file found but no document.crypto key configured to decrypt it");
    }
    Cipher cipher = Cipher.getInstance(TRANSFORMATION);
    cipher.init(
        Cipher.DECRYPT_MODE,
        key,
        new GCMParameterSpec(TAG_LENGTH_BITS, payload, LEGACY_MAGIC.length, IV_LENGTH));
    return cipher.doFinal(payload, LEGACY_PREFIX_LENGTH, payload.length - LEGACY_PREFIX_LENGTH);
  }

  private boolean startsWith(byte[] payload, byte[] prefix) {
    if (payload.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (payload[i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  private Map<String, SecretKey> loadKeys(Map<String, String> rawKeys) {
    Map<String, SecretKey> loaded = new LinkedHashMap<>();
    rawKeys.forEach(
        (keyId, keyMaterial) -> loaded.put(validKeyId(keyId), loadKey(keyId, keyMaterial)));
    return loaded;
  }

  private String validKeyId(String keyId) {
    int length = keyId.getBytes(StandardCharsets.UTF_8).length;
    if (length == 0 || length > MAX_KEY_ID_BYTES) {
      throw new IllegalStateException(
          "document.crypto key id must be 1.." + MAX_KEY_ID_BYTES + " bytes in UTF-8: " + keyId);
    }
    return keyId;
  }

  private SecretKey loadKey(String keyId, String base64Key) {
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(base64Key.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "document.crypto key '" + keyId + "' must be valid Base64", e);
    }
    if (decoded.length != KEY_LENGTH_BYTES) {
      throw new IllegalStateException(
          "document.crypto key '"
              + keyId
              + "' must decode to "
              + KEY_LENGTH_BYTES
              + " bytes (AES-256), got "
              + decoded.length);
    }
    return new SecretKeySpec(decoded, "AES");
  }
}
