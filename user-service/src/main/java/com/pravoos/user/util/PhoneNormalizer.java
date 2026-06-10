package com.pravoos.user.util;

public final class PhoneNormalizer {

    private PhoneNormalizer() {
    }

    public static String normalize(String phone) {
        if (phone == null) {
            return "";
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 11 && digits.charAt(0) == '8') {
            digits = "7" + digits.substring(1);
        }
        return digits.isEmpty() ? "" : "+" + digits;
    }
}
