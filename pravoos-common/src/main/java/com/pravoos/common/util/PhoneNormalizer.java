package com.pravoos.common.util;

import com.pravoos.common.exception.InvalidPhoneNumberException;

public final class PhoneNormalizer {

    private PhoneNormalizer() {
    }

    public static String normalize(String phone) {
        if (phone == null) {
            throw new InvalidPhoneNumberException();
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 11 && digits.charAt(0) == '8') {
            digits = "7" + digits.substring(1);
        }
        if (digits.length() != 11 || digits.charAt(0) != '7') {
            throw new InvalidPhoneNumberException();
        }
        return "+" + digits;
    }
}
