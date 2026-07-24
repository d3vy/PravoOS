package com.pravoos.ai.document.internal.search;

import java.time.LocalDate;
import java.util.UUID;

public record ChunkCandidate(
    UUID chunkId,
    int chunkIndex,
    String content,
    String documentTitle,
    boolean legislation,
    String actCanonical,
    String articleNumber,
    LocalDate editionDate,
    double score) {

  public ChunkCandidate withScore(double newScore) {
    return new ChunkCandidate(
        chunkId,
        chunkIndex,
        content,
        documentTitle,
        legislation,
        actCanonical,
        articleNumber,
        editionDate,
        newScore);
  }
}
