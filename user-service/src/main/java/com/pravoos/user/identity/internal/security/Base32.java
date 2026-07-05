package com.pravoos.user.identity.internal.security;

public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int[] DECODE_TABLE = new int[128];

    static {
        java.util.Arrays.fill(DECODE_TABLE, -1);
        for (int i = 0; i < ALPHABET.length(); i++) {
            DECODE_TABLE[ALPHABET.charAt(i)] = i;
        }
    }

    private Base32() {
    }

    public static String encode(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                bitsLeft -= 5;
                result.append(ALPHABET.charAt(index));
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            result.append(ALPHABET.charAt(index));
        }
        return result.toString();
    }

    public static byte[] decode(String encoded) {
        String normalized = encoded.trim().replace(" ", "").replace("=", "").toUpperCase();
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c >= DECODE_TABLE.length || DECODE_TABLE[c] < 0) {
                throw new IllegalArgumentException("Invalid Base32 character");
            }
            buffer = (buffer << 5) | DECODE_TABLE[c];
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return output.toByteArray();
    }
}
