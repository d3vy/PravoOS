package com.pravoos.ai.document.internal.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReciprocalRankFusionTest {

  private static final double RRF_K = 60.0;
  private static final double NO_BOOST = 0.0;

  private final UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private final UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private final UUID third = UUID.fromString("00000000-0000-0000-0000-000000000003");

  @Test
  void ranksChunkFoundByBothSourcesAboveChunksFoundByOne() {
    List<ChunkCandidate> vector = List.of(candidate(first, false), candidate(second, false));
    List<ChunkCandidate> lexical = List.of(candidate(third, false), candidate(second, false));

    List<ChunkCandidate> fused =
        ReciprocalRankFusion.fuse(
            List.of(
                new RankedSource("vector", vector, 1.0), new RankedSource("lexical", lexical, 1.0)),
            RRF_K,
            NO_BOOST);

    assertThat(fused).extracting(ChunkCandidate::chunkId).containsExactly(second, first, third);
  }

  @Test
  void deduplicatesCandidatesAcrossSources() {
    List<ChunkCandidate> vector = List.of(candidate(first, false));
    List<ChunkCandidate> lexical = List.of(candidate(first, false));

    List<ChunkCandidate> fused =
        ReciprocalRankFusion.fuse(
            List.of(
                new RankedSource("vector", vector, 1.0), new RankedSource("lexical", lexical, 1.0)),
            RRF_K,
            NO_BOOST);

    assertThat(fused).hasSize(1);
    assertThat(fused.get(0).score()).isEqualTo(2.0 / (RRF_K + 1));
  }

  @Test
  void appliesSourceWeights() {
    List<ChunkCandidate> vector = List.of(candidate(first, false));
    List<ChunkCandidate> lexical = List.of(candidate(second, false));

    List<ChunkCandidate> fused =
        ReciprocalRankFusion.fuse(
            List.of(
                new RankedSource("vector", vector, 1.0), new RankedSource("lexical", lexical, 0.1)),
            RRF_K,
            NO_BOOST);

    assertThat(fused).extracting(ChunkCandidate::chunkId).containsExactly(first, second);
  }

  @Test
  void ignoresSourceWithZeroWeight() {
    List<ChunkCandidate> lexical = List.of(candidate(second, false));

    List<ChunkCandidate> fused =
        ReciprocalRankFusion.fuse(
            List.of(
                new RankedSource("vector", List.of(candidate(first, false)), 1.0),
                new RankedSource("lexical", lexical, 0.0)),
            RRF_K,
            NO_BOOST);

    assertThat(fused).extracting(ChunkCandidate::chunkId).containsExactly(first);
  }

  @Test
  void legislationBoostWinsNearTiesOnly() {
    List<ChunkCandidate> vector = List.of(candidate(first, false), candidate(second, true));

    List<ChunkCandidate> boosted =
        ReciprocalRankFusion.fuse(List.of(new RankedSource("vector", vector, 1.0)), RRF_K, 0.005);
    List<ChunkCandidate> unboosted =
        ReciprocalRankFusion.fuse(
            List.of(new RankedSource("vector", vector, 1.0)), RRF_K, NO_BOOST);

    assertThat(boosted).extracting(ChunkCandidate::chunkId).containsExactly(second, first);
    assertThat(unboosted).extracting(ChunkCandidate::chunkId).containsExactly(first, second);
  }

  @Test
  void legislationBoostDoesNotOverrideStrongRelevanceGap() {
    List<ChunkCandidate> vector =
        List.of(candidate(first, false), candidate(third, false), candidate(second, true));
    List<ChunkCandidate> lexical = List.of(candidate(first, false));

    List<ChunkCandidate> fused =
        ReciprocalRankFusion.fuse(
            List.of(
                new RankedSource("vector", vector, 1.0), new RankedSource("lexical", lexical, 1.0)),
            RRF_K,
            0.005);

    assertThat(fused.get(0).chunkId()).isEqualTo(first);
  }

  @Test
  void returnsEmptyListWhenNoSourcesOrNoCandidates() {
    assertThat(ReciprocalRankFusion.fuse(List.of(), RRF_K, NO_BOOST)).isEmpty();
    assertThat(
            ReciprocalRankFusion.fuse(
                List.of(new RankedSource("vector", List.of(), 1.0)), RRF_K, NO_BOOST))
        .isEmpty();
  }

  @Test
  void rejectsNonPositiveRrfConstant() {
    assertThatThrownBy(
            () ->
                ReciprocalRankFusion.fuse(
                    List.of(new RankedSource("vector", List.of(candidate(first, false)), 1.0)),
                    0.0,
                    NO_BOOST))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private ChunkCandidate candidate(UUID id, boolean legislation) {
    return new ChunkCandidate(id, 0, "content-" + id, "doc", legislation, null, null, null, 0.0);
  }
}
