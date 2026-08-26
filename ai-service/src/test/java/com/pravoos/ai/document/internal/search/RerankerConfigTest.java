package com.pravoos.ai.document.internal.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.shared.config.HybridSearchProperties;
import org.junit.jupiter.api.Test;

class RerankerConfigTest {

  private final RerankerConfig config = new RerankerConfig();
  private final LlmClient llmClient = mock(LlmClient.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  private HybridSearchProperties properties(boolean rerankEnabled) {
    return new HybridSearchProperties(
        true,
        true,
        0.5,
        3,
        50,
        60.0,
        1.0,
        1.0,
        0.1,
        new HybridSearchProperties.Rerank(rerankEnabled, 10, 500, 2000, true));
  }

  @Test
  void reranker_returnsPassThroughReranker_whenRerankDisabled() {
    Reranker reranker = config.reranker(properties(false), llmClient, objectMapper);

    assertThat(reranker).isInstanceOf(PassThroughReranker.class);
  }

  @Test
  void reranker_returnsLlmReranker_whenRerankEnabled() {
    Reranker reranker = config.reranker(properties(true), llmClient, objectMapper);

    assertThat(reranker).isInstanceOf(LlmReranker.class);
  }
}
