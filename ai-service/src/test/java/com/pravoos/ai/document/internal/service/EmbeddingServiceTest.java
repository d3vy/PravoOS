package com.pravoos.ai.document.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.pravoos.ai.document.internal.model.entity.DocumentChunk;
import com.pravoos.ai.llm.api.EmbeddingResult;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.shared.config.LlmServiceProperties;
import com.pravoos.ai.shared.exception.LlmException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

  @Mock private LlmClient llmClient;

  @Test
  void constructor_throwsIllegalStateException_whenConfiguredDimensionsMismatchColumn() {
    LlmServiceProperties properties = new LlmServiceProperties("http://localhost", "secret", 42);

    assertThatThrownBy(() -> new EmbeddingService(llmClient, properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("42")
        .hasMessageContaining(String.valueOf(DocumentChunk.EMBEDDING_DIMENSIONS));
  }

  @Test
  void embed_returnsEmbedding_whenDimensionsMatch() {
    LlmServiceProperties properties =
        new LlmServiceProperties("http://localhost", "secret", DocumentChunk.EMBEDDING_DIMENSIONS);
    EmbeddingService service = new EmbeddingService(llmClient, properties);
    float[] embedding = new float[DocumentChunk.EMBEDDING_DIMENSIONS];
    when(llmClient.embed("text")).thenReturn(embedding);

    float[] result = service.embed("text");

    assertThat(result).isSameAs(embedding);
  }

  @Test
  void embed_throwsLlmException_whenDimensionsMismatch() {
    LlmServiceProperties properties =
        new LlmServiceProperties("http://localhost", "secret", DocumentChunk.EMBEDDING_DIMENSIONS);
    EmbeddingService service = new EmbeddingService(llmClient, properties);
    when(llmClient.embed("text")).thenReturn(new float[10]);

    assertThatThrownBy(() -> service.embed("text")).isInstanceOf(LlmException.class);
  }

  @Test
  void embedBatch_returnsResult_whenAllEmbeddingsMatchDimensions() {
    LlmServiceProperties properties =
        new LlmServiceProperties("http://localhost", "secret", DocumentChunk.EMBEDDING_DIMENSIONS);
    EmbeddingService service = new EmbeddingService(llmClient, properties);
    EmbeddingResult result =
        new EmbeddingResult(
            List.of(
                new float[DocumentChunk.EMBEDDING_DIMENSIONS],
                new float[DocumentChunk.EMBEDDING_DIMENSIONS]),
            100);
    when(llmClient.embedBatch(List.of("a", "b"))).thenReturn(result);

    EmbeddingResult actual = service.embedBatch(List.of("a", "b"));

    assertThat(actual).isSameAs(result);
  }

  @Test
  void embedBatch_throwsLlmException_whenAnyEmbeddingMismatchesDimensions() {
    LlmServiceProperties properties =
        new LlmServiceProperties("http://localhost", "secret", DocumentChunk.EMBEDDING_DIMENSIONS);
    EmbeddingService service = new EmbeddingService(llmClient, properties);
    EmbeddingResult result =
        new EmbeddingResult(
            List.of(new float[DocumentChunk.EMBEDDING_DIMENSIONS], new float[5]), 100);
    when(llmClient.embedBatch(List.of("a", "b"))).thenReturn(result);

    assertThatThrownBy(() -> service.embedBatch(List.of("a", "b")))
        .isInstanceOf(LlmException.class);
  }
}
