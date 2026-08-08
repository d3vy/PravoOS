package com.pravoos.ai.document.internal.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RankedSourceTest {

  @Test
  void constructor_replacesNullCandidatesWithEmptyList() {
    RankedSource source = new RankedSource("lexical", null, 1.0);

    assertThat(source.candidates()).isEmpty();
  }

  @Test
  void constructor_copiesCandidatesList() {
    ChunkCandidate candidate =
        new ChunkCandidate(UUID.randomUUID(), 0, "c", "t", false, null, null, LocalDate.now(), 1.0);
    List<ChunkCandidate> mutable = new java.util.ArrayList<>(List.of(candidate));

    RankedSource source = new RankedSource("vector", mutable, 1.0);
    mutable.clear();

    assertThat(source.candidates()).containsExactly(candidate);
  }

  @Test
  void constructor_throwsIllegalArgumentException_whenWeightNegative() {
    assertThatThrownBy(() -> new RankedSource("lexical", List.of(), -0.1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("lexical");
  }

  @Test
  void constructor_allowsZeroWeight() {
    RankedSource source = new RankedSource("lexical", List.of(), 0);

    assertThat(source.weight()).isZero();
  }
}
