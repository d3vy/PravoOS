package com.pravoos.user.security;

import com.pravoos.user.config.InternalSecretProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class InternalSecretVerifier {

    private final InternalSecretProperties secretProperties;

    public InternalSecretVerifier(InternalSecretProperties secretProperties) {
        this.secretProperties = secretProperties;
    }

    public void verify(String secret) {
        if (secret == null || !MessageDigest.isEqual(sha256(secretProperties.secret()), sha256(secret))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
