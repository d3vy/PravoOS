package com.pravoos.ai.document.internal.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RerankScoreParser {

    private static final Logger log = LoggerFactory.getLogger(RerankScoreParser.class);

    private static final TypeReference<List<RerankScore>> SCORES_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public RerankScoreParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<Integer, Double> parse(String rawResponse, int candidateCount) {
        String jsonArray = extractJsonArray(rawResponse);
        if (jsonArray == null) {
            log.warn("Reranker response contains no JSON array, falling back to fusion order");
            return Map.of();
        }

        List<RerankScore> scores;
        try {
            scores = objectMapper.readValue(jsonArray, SCORES_TYPE);
        } catch (Exception e) {
            log.warn("Failed to parse reranker response as JSON: {}", e.getMessage());
            return Map.of();
        }

        Map<Integer, Double> scoreByIndex = new LinkedHashMap<>();
        for (RerankScore score : scores) {
            if (score == null || score.id() == null || score.score() == null) {
                continue;
            }
            int index = score.id() - 1;
            if (index < 0 || index >= candidateCount) {
                continue;
            }
            scoreByIndex.putIfAbsent(index, score.score());
        }
        return scoreByIndex;
    }

    private String extractJsonArray(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }
        int start = rawResponse.indexOf('[');
        int end = rawResponse.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        return rawResponse.substring(start, end + 1);
    }

    record RerankScore(Integer id, Double score) {}
}
