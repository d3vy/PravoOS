package com.pravoos.ai.document.internal.search;

import java.util.List;

public class PassThroughReranker implements Reranker {

  @Override
  public RerankOutcome rerank(String query, List<ChunkCandidate> candidates, int topK) {
    if (candidates == null || candidates.isEmpty() || topK <= 0) {
      return RerankOutcome.free(List.of());
    }
    return RerankOutcome.free(candidates.subList(0, Math.min(topK, candidates.size())));
  }
}
