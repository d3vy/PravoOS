package com.pravoos.ai.document.internal.search;

import java.util.List;

public record RerankOutcome(List<ChunkCandidate> candidates, long llmTokens) {

  public static RerankOutcome free(List<ChunkCandidate> candidates) {
    return new RerankOutcome(candidates, 0L);
  }
}
