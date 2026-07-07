package com.pravoos.ai.document.internal.service;

import com.pravoos.ai.shared.config.FileCryptoProperties;
import com.pravoos.ai.shared.exception.DocumentProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class FileCryptoService {

    private static final Logger log = LoggerFactory.getLogger(FileCryptoService.class);

    private static final byte[] MAGIC = {0x50, 0x4F, 0x53, 0x31};
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public FileCryptoService(FileCryptoProperties properties) {
        this.secretKey = properties.hasKey() ? loadKey(properties.key()) : null;
        if (secretKey == null) {
            log.warn("File encryption disabled: document.crypto.key is not set. "
                    + "New files are written in plaintext.");
        }
    }

    public void encryptToFile(byte[] plaintext, Path target) {
        try {
            byte[] payload = secretKey == null ? plaintext : encrypt(plaintext);
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
            if (!isEncrypted(payload)) {
                return payload;
            }
            if (secretKey == null) {
                throw new DocumentProcessingException("Encrypted file found but no encryption key configured");
            }
            return decrypt(payload);
        } catch (GeneralSecurityException e) {
            throw new DocumentProcessingException("Failed to decrypt file: " + e.getMessage());
        } catch (java.io.IOException e) {
            throw new DocumentProcessingException("Failed to read file: " + e.getMessage());
        }
    }

    private byte[] encrypt(byte[] plaintext) throws GeneralSecurityException {
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);

        byte[] result = new byte[MAGIC.length + IV_LENGTH + ciphertext.length];
        System.arraycopy(MAGIC, 0, result, 0, MAGIC.length);
        System.arraycopy(iv, 0, result, MAGIC.length, IV_LENGTH);
        System.arraycopy(ciphertext, 0, result, MAGIC.length + IV_LENGTH, ciphertext.length);
        return result;
    }

    private byte[] decrypt(byte[] payload) throws GeneralSecurityException {
        byte[] iv = Arrays.copyOfRange(payload, MAGIC.length, MAGIC.length + IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(payload, MAGIC.length + IV_LENGTH, payload.length);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        return cipher.doFinal(ciphertext);
    }

    private boolean isEncrypted(byte[] payload) {
        if (payload.length < MAGIC.length + IV_LENGTH) {
            return false;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (payload[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private SecretKey loadKey(String base64Key) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("document.crypto.key must be valid Base64", e);
        }
        if (decoded.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "document.crypto.key must decode to " + KEY_LENGTH_BYTES + " bytes (AES-256), got " + decoded.length);
        }
        return new SecretKeySpec(decoded, "AES");
    }
}
