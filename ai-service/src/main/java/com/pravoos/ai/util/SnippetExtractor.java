package com.pravoos.ai.util;

import java.util.Locale;

public final class SnippetExtractor {

    private static final String ELLIPSIS = "…";

    private SnippetExtractor() {
    }

    public static String around(String content, String term, int radius) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (term == null || term.isBlank()) {
            return clampHead(normalized, radius * 2);
        }

        int matchIndex = normalized.toLowerCase(Locale.ROOT).indexOf(term.toLowerCase(Locale.ROOT));
        if (matchIndex < 0) {
            return clampHead(normalized, radius * 2);
        }

        int start = Math.max(0, matchIndex - radius);
        int end = Math.min(normalized.length(), matchIndex + term.length() + radius);
        String snippet = normalized.substring(start, end);
        if (start > 0) {
            snippet = ELLIPSIS + snippet;
        }
        if (end < normalized.length()) {
            snippet = snippet + ELLIPSIS;
        }
        return snippet;
    }

    private static String clampHead(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + ELLIPSIS;
    }
}
