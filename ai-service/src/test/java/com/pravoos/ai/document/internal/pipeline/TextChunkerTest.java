package com.pravoos.ai.document.internal.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkerTest {

  private final TextChunker chunker = new TextChunker();

  @Test
  void chunk_throwsIllegalArgumentException_whenChunkSizeNotPositive() {
    assertThatThrownBy(() -> chunker.chunk("text", 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void chunk_throwsIllegalArgumentException_whenOverlapNegative() {
    assertThatThrownBy(() -> chunker.chunk("text", 5, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void chunk_throwsIllegalArgumentException_whenOverlapGreaterOrEqualChunkSize() {
    assertThatThrownBy(() -> chunker.chunk("text", 5, 5))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void chunk_returnsEmptyList_whenTextNull() {
    assertThat(chunker.chunk(null, 5, 1)).isEmpty();
  }

  @Test
  void chunk_returnsEmptyList_whenTextBlank() {
    assertThat(chunker.chunk("   ", 5, 1)).isEmpty();
  }

  @Test
  void chunk_returnsSingleChunk_whenTextFitsWithinChunkSize() {
    List<String> chunks = chunker.chunk("one two three", 5, 1);

    assertThat(chunks).containsExactly("one two three");
  }

  @Test
  void chunk_splitsWithOverlap_whenTextExceedsChunkSize() {
    String text = "a b c d e f g h";

    List<String> chunks = chunker.chunk(text, 4, 1);

    assertThat(chunks).containsExactly("a b c d", "d e f g", "g h");
  }

  @Test
  void chunk_splitsWithoutOverlap_whenOverlapZero() {
    String text = "a b c d";

    List<String> chunks = chunker.chunk(text, 2, 0);

    assertThat(chunks).containsExactly("a b", "c d");
  }

  @Test
  void chunk_collapsesRepeatedWhitespace() {
    String text = "a   b\n\nc\td";

    List<String> chunks = chunker.chunk(text, 10, 1);

    assertThat(chunks).containsExactly("a b c d");
  }
}
