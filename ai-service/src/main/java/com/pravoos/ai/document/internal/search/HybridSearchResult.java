package com.pravoos.ai.document.internal.search;

import java.util.List;

public record HybridSearchResult(List<ChunkCandidate> candidates, long llmTokens) {

  public static HybridSearchResult empty() {
    return new HybridSearchResult(List.of(), 0L);
  }
}
