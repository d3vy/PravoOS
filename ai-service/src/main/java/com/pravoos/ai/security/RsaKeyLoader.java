package com.pravoos.ai.security;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class RsaKeyLoader {

    private RsaKeyLoader() {
    }

    public static RSAPublicKey loadPublicKey(String key) {
        try {
            byte[] der = decode(key);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid RSA public key", e);
        }
    }

    private static byte[] decode(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("RSA public key is not configured");
        }
        String normalized = key
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }
}
