package com.pravoos.ai.document.internal.service;

import static org.junit.jupiter.api.Assertions.*;

import com.pravoos.ai.shared.config.FileCryptoProperties;
import com.pravoos.ai.shared.exception.DocumentProcessingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileCryptoServiceTest {

  @TempDir Path tempDir;

  private static String randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  private FileCryptoService withKey(String base64Key) {
    return new FileCryptoService(FileCryptoProperties.ofSingleKey(base64Key));
  }

  @Test
  void encryptsThenDecryptsBackToOriginal() {
    FileCryptoService service = withKey(randomKey());
    byte[] original = "Секретный документ клиента".getBytes(StandardCharsets.UTF_8);
    Path file = tempDir.resolve("doc.bin");

    service.encryptToFile(original, file);

    assertArrayEquals(original, service.decryptFile(file));
  }

  @Test
  void ciphertextOnDiskDiffersFromPlaintext() throws Exception {
    FileCryptoService service = withKey(randomKey());
    byte[] original = "plaintext-marker".getBytes(StandardCharsets.UTF_8);
    Path file = tempDir.resolve("doc.bin");

    service.encryptToFile(original, file);

    byte[] onDisk = Files.readAllBytes(file);
    assertFalse(new String(onDisk, StandardCharsets.UTF_8).contains("plaintext-marker"));
    assertTrue(onDisk.length > original.length);
  }

  @Test
  void readsLegacyPlaintextFileWithoutMagicHeader() throws Exception {
    FileCryptoService service = withKey(randomKey());
    byte[] legacy = "старый незашифрованный файл".getBytes(StandardCharsets.UTF_8);
    Path file = tempDir.resolve("legacy.txt");
    Files.write(file, legacy);

    assertArrayEquals(legacy, service.decryptFile(file));
  }

  @Test
  void wrongKeyFailsToDecrypt() {
    Path file = tempDir.resolve("doc.bin");
    withKey(randomKey()).encryptToFile("data".getBytes(StandardCharsets.UTF_8), file);

    FileCryptoService otherKey = withKey(randomKey());
    assertThrows(DocumentProcessingException.class, () -> otherKey.decryptFile(file));
  }

  @Test
  void withoutKeyWritesAndReadsPlaintext() throws Exception {
    FileCryptoService service = withKey(null);
    byte[] original = "no-key-mode".getBytes(StandardCharsets.UTF_8);
    Path file = tempDir.resolve("doc.bin");

    service.encryptToFile(original, file);

    assertArrayEquals(original, Files.readAllBytes(file));
    assertArrayEquals(original, service.decryptFile(file));
  }

  @Test
  void decryptsFileWrittenWithARetiredKeyAfterRotation() {
    String oldKey = randomKey();
    String newKey = randomKey();
    byte[] original = "документ до ротации".getBytes(StandardCharsets.UTF_8);
    Path file = tempDir.resolve("doc.bin");

    new FileCryptoService(new FileCryptoProperties("v1", Map.of("v1", oldKey), null))
        .encryptToFile(original, file);

    Map<String, String> bothKeys = new LinkedHashMap<>();
    bothKeys.put("v1", oldKey);
    bothKeys.put("v2", newKey);
    FileCryptoService afterRotation =
        new FileCryptoService(new FileCryptoProperties("v2", bothKeys, null));

    assertArrayEquals(original, afterRotation.decryptFile(file));
  }

  @Test
  void newFilesAreWrittenWithTheActiveKeyOnly() {
    String oldKey = randomKey();
    String newKey = randomKey();
    Map<String, String> bothKeys = new LinkedHashMap<>();
    bothKeys.put("v1", oldKey);
    bothKeys.put("v2", newKey);
    byte[] original = "документ после ротации".getBytes(StandardCharsets.UTF_8);
    Path file = tempDir.resolve("doc.bin");

    new FileCryptoService(new FileCryptoProperties("v2", bothKeys, null))
        .encryptToFile(original, file);

    FileCryptoService activeKeyOnly =
        new FileCryptoService(new FileCryptoProperties("v2", Map.of("v2", newKey), null));
    assertArrayEquals(original, activeKeyOnly.decryptFile(file));

    FileCryptoService retiredKeyOnly =
        new FileCryptoService(new FileCryptoProperties("v1", Map.of("v1", oldKey), null));
    assertThrows(DocumentProcessingException.class, () -> retiredKeyOnly.decryptFile(file));
  }

  @Test
  void rejectsAnActiveKeyIdThatHasNoConfiguredKey() {
    Map<String, String> keys = Map.of("v1", randomKey());
    assertThrows(
        IllegalStateException.class,
        () -> new FileCryptoService(new FileCryptoProperties("v2", keys, null)));
  }

  @Test
  void requiresAnActiveKeyIdWhenSeveralKeysAreConfigured() {
    Map<String, String> keys = new LinkedHashMap<>();
    keys.put("v1", randomKey());
    keys.put("v2", randomKey());
    assertThrows(
        IllegalStateException.class,
        () -> new FileCryptoService(new FileCryptoProperties(null, keys, null)));
  }

  @Test
  void rejectsAFileWhoseKeyIdWasTamperedWith() throws Exception {
    String keyId = "v1";
    FileCryptoService service =
        new FileCryptoService(new FileCryptoProperties(keyId, Map.of(keyId, randomKey()), null));
    Path file = tempDir.resolve("doc.bin");
    service.encryptToFile("данные".getBytes(StandardCharsets.UTF_8), file);

    byte[] onDisk = Files.readAllBytes(file);
    onDisk[5] = (byte) 'X';
    Files.write(file, onDisk);

    assertThrows(DocumentProcessingException.class, () -> service.decryptFile(file));
  }

  @Test
  void rejectsKeyOfWrongLength() {
    String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
    assertThrows(IllegalStateException.class, () -> withKey(shortKey));
  }
}
