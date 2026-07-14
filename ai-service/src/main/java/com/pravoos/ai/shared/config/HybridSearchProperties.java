package com.pravoos.ai.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "document.search")
public record HybridSearchProperties(
        boolean lexicalEnabled,
        double maxDistance,
        int candidateMultiplier,
        int maxCandidates,
        double rrfK,
        double vectorWeight,
        double lexicalWeight,
        double legislationBoost,
        Rerank rerank
) {

    public record Rerank(
            boolean enabled,
            int topN,
            int maxCharsPerCandidate,
            int maxTokens,
            boolean failOpen
    ) {}

    public int candidateLimit(int topK) {
        int requested = Math.max(topK, topK * Math.max(candidateMultiplier, 1));
        return Math.min(requested, Math.max(maxCandidates, topK));
    }

    public int rerankInputLimit(int topK) {
        return rerank.enabled() ? Math.max(topK, rerank.topN()) : topK;
    }
}
