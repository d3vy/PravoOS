package com.pravoos.ai.document.api;

import java.util.List;

public record RetrievedChunks(List<RetrievedChunk> chunks, long llmTokens) {

  public static RetrievedChunks empty() {
    return new RetrievedChunks(List.of(), 0L);
  }
}
