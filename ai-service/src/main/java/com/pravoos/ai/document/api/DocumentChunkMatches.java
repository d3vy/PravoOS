package com.pravoos.ai.document.api;

import java.util.List;

public record DocumentChunkMatches(
    List<DocumentChunkMatch> matches, long embeddingTokens, long rerankTokens) {

  public static DocumentChunkMatches empty() {
    return new DocumentChunkMatches(List.of(), 0L, 0L);
  }

  public long totalTokens() {
    return embeddingTokens + rerankTokens;
  }
}
