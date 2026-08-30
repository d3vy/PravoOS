package com.pravoos.ai.document.internal.search;

import com.pravoos.ai.document.internal.service.EmbeddingService;
import com.pravoos.ai.shared.config.HybridSearchProperties;
import com.pravoos.ai.shared.util.Futures;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class HybridSearchService {

  private static final Logger log = LoggerFactory.getLogger(HybridSearchService.class);

  private static final String VECTOR_SOURCE = "vector";
  private static final String LEXICAL_SOURCE = "lexical";

  private final EmbeddingService embeddingService;
  private final VectorChunkSearchRepository vectorSearchRepository;
  private final LexicalChunkSearchRepository lexicalSearchRepository;
  private final Reranker reranker;
  private final HybridSearchProperties properties;
  private final Executor searchExecutor;

  public HybridSearchService(
      EmbeddingService embeddingService,
      VectorChunkSearchRepository vectorSearchRepository,
      LexicalChunkSearchRepository lexicalSearchRepository,
      Reranker reranker,
      HybridSearchProperties properties,
      @Qualifier("hybridSearchExecutor") Executor searchExecutor) {
    this.embeddingService = embeddingService;
    this.vectorSearchRepository = vectorSearchRepository;
    this.lexicalSearchRepository = lexicalSearchRepository;
    this.reranker = reranker;
    this.properties = properties;
    this.searchExecutor = searchExecutor;
  }

  public HybridSearchResult search(String query, int topK, ChunkSearchScope scope) {
    if (query == null || query.isBlank() || topK <= 0) {
      return HybridSearchResult.empty();
    }
    return search(query, embeddingService.embed(query), topK, scope);
  }

  public HybridSearchResult search(
      String query, float[] queryEmbedding, int topK, ChunkSearchScope scope) {
    if (query == null || query.isBlank() || queryEmbedding == null || topK <= 0) {
      return HybridSearchResult.empty();
    }

    int candidateLimit = properties.candidateLimit(topK);
    CompletableFuture<List<ChunkCandidate>> pendingLexicalCandidates =
        CompletableFuture.supplyAsync(
            () -> lexicalCandidates(query, candidateLimit, scope), searchExecutor);
    List<ChunkCandidate> vectorCandidates =
        vectorSearchRepository.search(queryEmbedding, candidateLimit, scope);
    List<ChunkCandidate> lexicalCandidates = Futures.join(pendingLexicalCandidates);

    List<ChunkCandidate> fused =
        ReciprocalRankFusion.fuse(
            List.of(
                new RankedSource(VECTOR_SOURCE, vectorCandidates, properties.vectorWeight()),
                new RankedSource(LEXICAL_SOURCE, lexicalCandidates, properties.lexicalWeight())),
            properties.rrfK(),
            properties.legislationBoost());

    if (fused.isEmpty()) {
      return HybridSearchResult.empty();
    }

    int rerankInputSize = Math.min(fused.size(), properties.rerankInputLimit(topK));
    List<ChunkCandidate> rerankInput = fused.subList(0, rerankInputSize);

    log.debug(
        "Hybrid search: {} vector + {} lexical → {} fused → rerank top-{} → return top-{}",
        vectorCandidates.size(),
        lexicalCandidates.size(),
        fused.size(),
        rerankInputSize,
        topK);

    RerankOutcome reranked = reranker.rerank(query, rerankInput, topK);
    return new HybridSearchResult(reranked.candidates(), reranked.llmTokens());
  }

  private List<ChunkCandidate> lexicalCandidates(
      String query, int candidateLimit, ChunkSearchScope scope) {
    if (!properties.lexicalEnabled()) {
      return List.of();
    }
    try {
      return lexicalSearchRepository.search(query, candidateLimit, scope);
    } catch (DataAccessException | PersistenceException e) {
      log.warn("Lexical search failed, continuing with vector-only results: {}", e.getMessage());
      return List.of();
    }
  }
}
