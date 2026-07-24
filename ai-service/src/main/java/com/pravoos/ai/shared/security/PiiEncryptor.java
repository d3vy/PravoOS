package com.pravoos.ai.shared.security;

import com.pravoos.ai.shared.config.PiiCryptoProperties;
import com.pravoos.ai.shared.exception.DocumentProcessingException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class PiiEncryptor {

    private static final Logger log = LoggerFactory.getLogger(PiiEncryptor.class);

    private static final String MARKER = "pii1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public PiiEncryptor(PiiCryptoProperties properties) {
        this.secretKey = properties.hasKey() ? loadKey(properties.key()) : null;
        if (secretKey == null) {
            log.warn("PII encryption disabled: pii.crypto.key is not set. "
                    + "New personal data columns are written in plaintext.");
        }
    }

    @PostConstruct
    void register() {
        PiiCryptoHolder.register(this);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || secretKey == null) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, IV_LENGTH, ciphertext.length);
            return MARKER + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new DocumentProcessingException("Failed to encrypt personal data: " + e.getMessage());
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(MARKER)) {
            return stored;
        }
        if (secretKey == null) {
            throw new DocumentProcessingException("Encrypted personal data found but no pii.crypto.key configured");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(MARKER.length()));
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new DocumentProcessingException("Failed to decrypt personal data: " + e.getMessage());
        }
    }

    private SecretKey loadKey(String base64Key) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("pii.crypto.key must be valid Base64", e);
        }
        if (decoded.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "pii.crypto.key must decode to " + KEY_LENGTH_BYTES + " bytes (AES-256), got " + decoded.length);
        }
        return new SecretKeySpec(decoded, "AES");
    }
}
