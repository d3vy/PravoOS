package com.pravoos.user.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

@Component
public class TotpGenerator {

    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int TIME_STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int SECRET_BYTE_LENGTH = 20;
    private static final int VERIFICATION_WINDOW = 1;
    private static final int DIVISOR = 1_000_000;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateSecret() {
        byte[] buffer = new byte[SECRET_BYTE_LENGTH];
        secureRandom.nextBytes(buffer);
        return Base32.encode(buffer);
    }

    public boolean verify(String base32Secret, String code) {
        if (code == null || code.length() != DIGITS) {
            return false;
        }
        long currentStep = currentTimeStep();
        for (int offset = -VERIFICATION_WINDOW; offset <= VERIFICATION_WINDOW; offset++) {
            String candidate = generateCode(base32Secret, currentStep + offset);
            if (constantTimeEquals(candidate, code)) {
                return true;
            }
        }
        return false;
    }

    public String otpAuthUri(String base32Secret, String accountEmail, String issuer) {
        String label = urlEncode(issuer) + ":" + urlEncode(accountEmail);
        return "otpauth://totp/" + label
                + "?secret=" + base32Secret
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + TIME_STEP_SECONDS;
    }

    private long currentTimeStep() {
        return System.currentTimeMillis() / 1000L / TIME_STEP_SECONDS;
    }

    private String generateCode(String base32Secret, long step) {
        byte[] key = Base32.decode(base32Secret);
        byte[] data = ByteBuffer.allocate(Long.BYTES).putLong(step).array();
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % DIVISOR;
            return String.format("%0" + DIGITS + "d", otp);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to generate TOTP code", ex);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
