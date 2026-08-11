package com.pravoos.ai.document.internal.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PassThroughRerankerTest {

  private final PassThroughReranker reranker = new PassThroughReranker();

  private ChunkCandidate candidate(double score) {
    return new ChunkCandidate(
        UUID.randomUUID(), 0, "content", "title", false, null, null, LocalDate.now(), score);
  }

  @Test
  void rerank_returnsEmptyList_whenCandidatesNull() {
    assertThat(reranker.rerank("query", null, 5).candidates()).isEmpty();
  }

  @Test
  void rerank_returnsEmptyList_whenCandidatesEmpty() {
    assertThat(reranker.rerank("query", List.of(), 5).candidates()).isEmpty();
  }

  @Test
  void rerank_returnsEmptyList_whenTopKZeroOrNegative() {
    List<ChunkCandidate> candidates = List.of(candidate(1.0));

    assertThat(reranker.rerank("query", candidates, 0).candidates()).isEmpty();
    assertThat(reranker.rerank("query", candidates, -1).candidates()).isEmpty();
  }

  @Test
  void rerank_truncatesToTopK_preservingOriginalOrder() {
    List<ChunkCandidate> candidates = List.of(candidate(1.0), candidate(2.0), candidate(3.0));

    List<ChunkCandidate> result = reranker.rerank("query", candidates, 2).candidates();

    assertThat(result).containsExactly(candidates.get(0), candidates.get(1));
  }

  @Test
  void rerank_returnsAllCandidates_whenTopKExceedsSize() {
    List<ChunkCandidate> candidates = List.of(candidate(1.0));

    List<ChunkCandidate> result = reranker.rerank("query", candidates, 10).candidates();

    assertThat(result).containsExactlyElementsOf(candidates);
  }
}
