package com.pravoos.ai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HybridSearchPropertiesTest {

  private HybridSearchProperties properties(
      int candidateMultiplier, int maxCandidates, HybridSearchProperties.Rerank rerank) {
    return new HybridSearchProperties(
        true, true, 0.85, candidateMultiplier, maxCandidates, 60.0, 0.5, 0.5, 0.005, rerank);
  }

  @Test
  void candidateLimitScalesByMultiplierWithinMaxCandidates() {
    HybridSearchProperties properties = properties(3, 50, rerank(false));

    assertThat(properties.candidateLimit(10)).isEqualTo(30);
  }

  @Test
  void candidateLimitClampedToMaxCandidates() {
    HybridSearchProperties properties = properties(10, 20, rerank(false));

    assertThat(properties.candidateLimit(10)).isEqualTo(20);
  }

  @Test
  void candidateLimitFallsBackToTopKWhenMaxCandidatesBelowTopK() {
    HybridSearchProperties properties = properties(1, 5, rerank(false));

    assertThat(properties.candidateLimit(10)).isEqualTo(10);
  }

  @Test
  void candidateLimitTreatsNonPositiveMultiplierAsOne() {
    HybridSearchProperties properties = properties(0, 50, rerank(false));

    assertThat(properties.candidateLimit(10)).isEqualTo(10);
  }

  @Test
  void rerankInputLimitUsesTopNWhenRerankEnabledAndLarger() {
    HybridSearchProperties properties = properties(3, 50, rerank(true, 25));

    assertThat(properties.rerankInputLimit(10)).isEqualTo(25);
  }

  @Test
  void rerankInputLimitUsesTopKWhenRerankEnabledButTopNSmaller() {
    HybridSearchProperties properties = properties(3, 50, rerank(true, 5));

    assertThat(properties.rerankInputLimit(10)).isEqualTo(10);
  }

  @Test
  void rerankInputLimitUsesTopKWhenRerankDisabled() {
    HybridSearchProperties properties = properties(3, 50, rerank(false, 25));

    assertThat(properties.rerankInputLimit(10)).isEqualTo(10);
  }

  private HybridSearchProperties.Rerank rerank(boolean enabled) {
    return rerank(enabled, 20);
  }

  private HybridSearchProperties.Rerank rerank(boolean enabled, int topN) {
    return new HybridSearchProperties.Rerank(enabled, topN, 500, 200, true);
  }
}
