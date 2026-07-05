package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.shared.model.enums.CitationType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CitationExtractor {

    private static final int FLAGS =
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;
    private static final int ACT_WINDOW_CHARS = 60;

    private static final Pattern COURT_CASE = Pattern.compile(
            "(?<![\\p{L}\\d/])[А-Яа-яЁё]\\d{1,2}-\\d{1,7}/\\d{4}", FLAGS);

    private static final Pattern STATUTE = Pattern.compile(
            "(?:ст\\.?|стат(?:ья|ьи|ье|ьей|ей))\\s*№?\\s*(\\d+(?:\\.\\d+)?)", FLAGS);

    public record ExtractedCitation(CitationType type, String raw, String core, String actCanonical) {}

    private final LegalActRegistry legalActRegistry;

    public CitationExtractor(LegalActRegistry legalActRegistry) {
        this.legalActRegistry = legalActRegistry;
    }

    public List<ExtractedCitation> extract(String text, int limit) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Map<String, ExtractedCitation> deduped = new LinkedHashMap<>();
        extractCourtCases(text, deduped);
        extractStatutes(text, deduped);

        List<ExtractedCitation> result = new ArrayList<>(deduped.values());
        if (limit > 0 && result.size() > limit) {
            return result.subList(0, limit);
        }
        return result;
    }

    private void extractCourtCases(String text, Map<String, ExtractedCitation> deduped) {
        Matcher matcher = COURT_CASE.matcher(text);
        while (matcher.find()) {
            String number = matcher.group().strip().toUpperCase();
            String key = CitationType.COURT_CASE + "|" + number;
            deduped.putIfAbsent(key, new ExtractedCitation(CitationType.COURT_CASE, number, number, null));
        }
    }

    private void extractStatutes(String text, Map<String, ExtractedCitation> deduped) {
        Matcher matcher = STATUTE.matcher(text);
        while (matcher.find()) {
            String article = matcher.group(1);
            String window = text.substring(matcher.start(),
                    Math.min(text.length(), matcher.end() + ACT_WINDOW_CHARS));
            var act = legalActRegistry.recognize(window);
            String actCanonical = act.map(LegalActRegistry.ActMatch::canonicalName).orElse(null);
            String raw = act.map(a -> matcher.group().strip() + " " + a.matchedText())
                    .orElse(matcher.group().strip());
            String key = CitationType.STATUTE + "|" + article + "|" + (actCanonical == null ? "?" : actCanonical);
            deduped.putIfAbsent(key, new ExtractedCitation(CitationType.STATUTE, raw, article, actCanonical));
        }
    }
}
