package com.pravoos.ai.document.internal.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.shared.config.HybridSearchProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class VectorChunkSearchRepositoryTest {

  private final VectorChunkSearchRepository repository =
      new VectorChunkSearchRepository(
          new HybridSearchProperties(
              true,
              true,
              0.5,
              3,
              50,
              60.0,
              1.0,
              1.0,
              0.1,
              new HybridSearchProperties.Rerank(false, 10, 500, 2000, true)));

  @Test
  void search_returnsEmptyList_whenEmbeddingNull() {
    List<ChunkCandidate> result = repository.search(null, 5, ChunkSearchScope.knowledgeBase());

    assertThat(result).isEmpty();
  }

  @Test
  void search_returnsEmptyList_whenEmbeddingEmpty() {
    List<ChunkCandidate> result =
        repository.search(new float[0], 5, ChunkSearchScope.knowledgeBase());

    assertThat(result).isEmpty();
  }

  @Test
  void search_returnsEmptyList_whenLimitZeroOrNegative() {
    float[] embedding = {0.1f, 0.2f};

    assertThat(repository.search(embedding, 0, ChunkSearchScope.knowledgeBase())).isEmpty();
    assertThat(repository.search(embedding, -1, ChunkSearchScope.knowledgeBase())).isEmpty();
  }
}
