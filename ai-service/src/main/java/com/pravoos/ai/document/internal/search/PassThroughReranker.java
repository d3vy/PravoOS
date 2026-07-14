package com.pravoos.ai.document.internal.search;

import java.util.List;

public class PassThroughReranker implements Reranker {

    @Override
    public List<ChunkCandidate> rerank(String query, List<ChunkCandidate> candidates, int topK) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }
        return candidates.subList(0, Math.min(topK, candidates.size()));
    }
}
