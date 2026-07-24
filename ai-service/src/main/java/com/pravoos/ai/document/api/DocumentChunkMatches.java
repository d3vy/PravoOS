package com.pravoos.ai.document.api;

import java.util.List;

public record DocumentChunkMatches(List<DocumentChunkMatch> matches, long embeddingTokens) {

  public static DocumentChunkMatches empty() {
    return new DocumentChunkMatches(List.of(), 0L);
  }
}
