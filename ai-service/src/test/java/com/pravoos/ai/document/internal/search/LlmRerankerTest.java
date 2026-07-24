package com.pravoos.ai.document.internal.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmOptions;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.llm.api.LlmUsage;
import com.pravoos.ai.shared.config.HybridSearchProperties;
import com.pravoos.ai.shared.exception.LlmException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LlmRerankerTest {

  private static final String QUERY = "неустойка по ст. 395 ГК РФ";

  @Mock private LlmClient llmClient;

  private final UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private final UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private final UUID third = UUID.fromString("00000000-0000-0000-0000-000000000003");

  @Test
  void reordersCandidatesByModelScoresAndTruncatesToTopK() {
    stubResponse("[{\"id\":1,\"score\":2},{\"id\":2,\"score\":9},{\"id\":3,\"score\":5}]");

    List<ChunkCandidate> reranked =
        reranker(failOpen(true))
            .rerank(QUERY, List.of(candidate(first), candidate(second), candidate(third)), 2);

    assertThat(reranked).extracting(ChunkCandidate::chunkId).containsExactly(second, third);
    assertThat(reranked.get(0).score()).isEqualTo(9.0);
  }

  @Test
  void keepsFusionOrderForCandidatesTheModelDidNotScore() {
    stubResponse("[{\"id\":3,\"score\":8}]");

    List<ChunkCandidate> reranked =
        reranker(failOpen(true))
            .rerank(QUERY, List.of(candidate(first), candidate(second), candidate(third)), 3);

    assertThat(reranked).extracting(ChunkCandidate::chunkId).containsExactly(third, first, second);
  }

  @Test
  void usesRerankModelProfileWithZeroTemperature() {
    stubResponse("[{\"id\":1,\"score\":1},{\"id\":2,\"score\":2}]");

    reranker(failOpen(true)).rerank(QUERY, List.of(candidate(first), candidate(second)), 2);

    ArgumentCaptor<LlmOptions> options = ArgumentCaptor.forClass(LlmOptions.class);
    verify(llmClient).complete(anyString(), anyList(), anyString(), options.capture());
    assertThat(options.getValue().modelProfile()).isEqualTo(LlmOptions.RERANK_PROFILE);
    assertThat(options.getValue().temperature()).isZero();
  }

  @Test
  void fallsBackToFusionOrderWhenModelFailsAndFailOpen() {
    when(llmClient.complete(anyString(), anyList(), anyString(), any(LlmOptions.class)))
        .thenThrow(new LlmException("upstream down"));

    List<ChunkCandidate> reranked =
        reranker(failOpen(true))
            .rerank(QUERY, List.of(candidate(first), candidate(second), candidate(third)), 2);

    assertThat(reranked).extracting(ChunkCandidate::chunkId).containsExactly(first, second);
  }

  @Test
  void fallsBackToFusionOrderWhenResponseIsUnparseable() {
    stubResponse("не могу оценить фрагменты");

    List<ChunkCandidate> reranked =
        reranker(failOpen(true)).rerank(QUERY, List.of(candidate(first), candidate(second)), 2);

    assertThat(reranked).extracting(ChunkCandidate::chunkId).containsExactly(first, second);
  }

  @Test
  void propagatesFailureWhenFailOpenDisabled() {
    stubResponse("мусор");

    assertThatThrownBy(
            () ->
                reranker(failOpen(false))
                    .rerank(QUERY, List.of(candidate(first), candidate(second)), 2))
        .isInstanceOf(LlmException.class);
  }

  @Test
  void skipsModelCallForEmptyOrSingleCandidateList() {
    Reranker reranker = reranker(failOpen(true));

    assertThat(reranker.rerank(QUERY, List.of(), 5)).isEmpty();
    assertThat(reranker.rerank(QUERY, List.of(candidate(first)), 5))
        .extracting(ChunkCandidate::chunkId)
        .containsExactly(first);
    verify(llmClient, never()).complete(anyString(), anyList(), anyString(), any(LlmOptions.class));
  }

  private void stubResponse(String content) {
    when(llmClient.complete(anyString(), anyList(), anyString(), any(LlmOptions.class)))
        .thenReturn(new LlmResult(content, LlmUsage.EMPTY));
  }

  private Reranker reranker(HybridSearchProperties.Rerank properties) {
    return new LlmReranker(
        llmClient,
        new RerankScoreParser(new ObjectMapper()),
        new PassThroughReranker(),
        properties);
  }

  private HybridSearchProperties.Rerank failOpen(boolean failOpen) {
    return new HybridSearchProperties.Rerank(true, 20, 700, 400, failOpen);
  }

  private ChunkCandidate candidate(UUID id) {
    return new ChunkCandidate(id, 0, "content-" + id, "doc", false, null, null, null, 0.01);
  }
}
