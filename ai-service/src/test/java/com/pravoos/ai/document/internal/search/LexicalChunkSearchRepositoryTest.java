package com.pravoos.ai.document.internal.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LexicalChunkSearchRepositoryTest {

  private final LexicalChunkSearchRepository repository = new LexicalChunkSearchRepository();

  @Test
  void search_returnsEmptyList_whenQueryTextNull() {
    List<ChunkCandidate> result = repository.search(null, 5, ChunkSearchScope.knowledgeBase());

    assertThat(result).isEmpty();
  }

  @Test
  void search_returnsEmptyList_whenQueryTextBlank() {
    List<ChunkCandidate> result = repository.search("   ", 5, ChunkSearchScope.knowledgeBase());

    assertThat(result).isEmpty();
  }

  @Test
  void search_returnsEmptyList_whenLimitZeroOrNegative() {
    assertThat(repository.search("query", 0, ChunkSearchScope.knowledgeBase())).isEmpty();
    assertThat(repository.search("query", -1, ChunkSearchScope.knowledgeBase())).isEmpty();
  }
}
