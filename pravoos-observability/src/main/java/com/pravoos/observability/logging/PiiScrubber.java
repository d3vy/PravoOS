package com.pravoos.observability.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PiiScrubber {

    private static final String REDACTED = "[redacted]";

    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern RUSSIAN_PHONE = Pattern.compile("(?<![0-9])(?:\\+7|8)[\\s(-]?\\d{3}[\\s)-]?\\d{3}[\\s-]?\\d{2}[\\s-]?\\d{2}(?![0-9])");

    public String scrub(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String scrubbed = JWT.matcher(value).replaceAll(REDACTED);
        scrubbed = BEARER.matcher(scrubbed).replaceAll("Bearer " + REDACTED);
        scrubbed = maskEmails(scrubbed);
        scrubbed = RUSSIAN_PHONE.matcher(scrubbed).replaceAll(REDACTED);
        return scrubbed;
    }

    private String maskEmails(String value) {
        Matcher matcher = EMAIL.matcher(value);
        StringBuilder masked = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(masked, Matcher.quoteReplacement(maskEmail(matcher.group())));
        }
        matcher.appendTail(masked);
        return masked.toString();
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        String visible = localPart.substring(0, Math.min(2, localPart.length()));
        return visible + "***" + domain;
    }
}
