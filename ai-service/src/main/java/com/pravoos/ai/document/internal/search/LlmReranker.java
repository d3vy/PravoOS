package com.pravoos.ai.document.internal.search;

import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmOptions;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.shared.config.HybridSearchProperties;
import com.pravoos.ai.shared.exception.LlmException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LlmReranker implements Reranker {

  private static final Logger log = LoggerFactory.getLogger(LlmReranker.class);

  private static final double RERANK_TEMPERATURE = 0.0;
  private static final double MISSING_SCORE = -1.0;

  private final LlmClient llmClient;
  private final RerankScoreParser scoreParser;
  private final Reranker fallbackReranker;
  private final HybridSearchProperties.Rerank properties;

  public LlmReranker(
      LlmClient llmClient,
      RerankScoreParser scoreParser,
      Reranker fallbackReranker,
      HybridSearchProperties.Rerank properties) {
    this.llmClient = llmClient;
    this.scoreParser = scoreParser;
    this.fallbackReranker = fallbackReranker;
    this.properties = properties;
  }

  @Override
  public RerankOutcome rerank(String query, List<ChunkCandidate> candidates, int topK) {
    if (candidates == null || candidates.isEmpty() || topK <= 0) {
      return RerankOutcome.free(List.of());
    }
    if (candidates.size() <= 1) {
      return fallbackReranker.rerank(query, candidates, topK);
    }

    Map<Integer, Double> scoreByIndex;
    long spentTokens;
    try {
      LlmResult result =
          llmClient.complete(
              RerankPrompt.SYSTEM_PROMPT,
              List.of(),
              RerankPrompt.buildUserMessage(query, candidates, properties.maxCharsPerCandidate()),
              LlmOptions.rerank(properties.maxTokens(), RERANK_TEMPERATURE));
      spentTokens = result.usage() == null ? 0L : result.usage().totalTokens();
      scoreByIndex = scoreParser.parse(result.content(), candidates.size());
    } catch (RuntimeException e) {
      return onFailure(query, candidates, topK, "reranker call failed: " + e.getMessage(), 0L);
    }

    if (scoreByIndex.isEmpty()) {
      return onFailure(query, candidates, topK, "reranker returned no usable scores", spentTokens);
    }

    return new RerankOutcome(
        reorder(candidates, scoreByIndex).subList(0, Math.min(topK, candidates.size())),
        spentTokens);
  }

  private List<ChunkCandidate> reorder(
      List<ChunkCandidate> candidates, Map<Integer, Double> scoreByIndex) {
    record Scored(ChunkCandidate candidate, double rerankScore, int fusionPosition) {}

    return IntStream.range(0, candidates.size())
        .mapToObj(
            i -> new Scored(candidates.get(i), scoreByIndex.getOrDefault(i, MISSING_SCORE), i))
        .sorted(
            Comparator.comparingDouble(Scored::rerankScore)
                .reversed()
                .thenComparingInt(Scored::fusionPosition))
        .map(scored -> scored.candidate().withScore(scored.rerankScore()))
        .toList();
  }

  private RerankOutcome onFailure(
      String query, List<ChunkCandidate> candidates, int topK, String reason, long spentTokens) {
    if (!properties.failOpen()) {
      throw new LlmException("Reranking failed: " + reason);
    }
    log.warn("Falling back to fusion order — {}", reason);
    return new RerankOutcome(
        fallbackReranker.rerank(query, candidates, topK).candidates(), spentTokens);
  }
}
