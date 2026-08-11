package com.pravoos.ai.document.internal;

import com.pravoos.ai.document.api.DocumentChunkMatch;
import com.pravoos.ai.document.api.DocumentChunkMatches;
import com.pravoos.ai.document.api.DocumentRetrieval;
import com.pravoos.ai.document.api.RetrievedChunk;
import com.pravoos.ai.document.api.RetrievedChunks;
import com.pravoos.ai.document.internal.search.ChunkCandidate;
import com.pravoos.ai.document.internal.search.ChunkSearchScope;
import com.pravoos.ai.document.internal.search.HybridSearchResult;
import com.pravoos.ai.document.internal.search.HybridSearchService;
import com.pravoos.ai.document.internal.service.EmbeddingService;
import com.pravoos.ai.llm.api.EmbeddingResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class DocumentRetrievalImpl implements DocumentRetrieval {

  private final HybridSearchService hybridSearchService;
  private final EmbeddingService embeddingService;

  DocumentRetrievalImpl(
      HybridSearchService hybridSearchService, EmbeddingService embeddingService) {
    this.hybridSearchService = hybridSearchService;
    this.embeddingService = embeddingService;
  }

  @Override
  public RetrievedChunks retrieveKnowledgeBase(String query, int topK) {
    return toRetrievedChunks(
        hybridSearchService.search(query, topK, ChunkSearchScope.knowledgeBase()));
  }

  @Override
  public RetrievedChunks retrieveForCase(String query, int topK, UUID caseId) {
    return toRetrievedChunks(
        hybridSearchService.search(query, topK, ChunkSearchScope.forCase(caseId)));
  }

  @Override
  public DocumentChunkMatches retrieveInDocument(List<String> queries, int topK, UUID documentId) {
    if (queries == null || topK <= 0) {
      return DocumentChunkMatches.empty();
    }
    List<String> effectiveQueries =
        queries.stream().filter(query -> query != null && !query.isBlank()).toList();
    if (effectiveQueries.isEmpty()) {
      return DocumentChunkMatches.empty();
    }

    EmbeddingResult embedded = embeddingService.embedBatch(effectiveQueries);
    ChunkSearchScope scope = ChunkSearchScope.forDocument(documentId);
    Map<UUID, DocumentChunkMatch> bestByChunk = new LinkedHashMap<>();
    long rerankTokens = 0L;
    for (int index = 0; index < effectiveQueries.size(); index++) {
      HybridSearchResult result =
          hybridSearchService.search(
              effectiveQueries.get(index), embedded.embeddings().get(index), topK, scope);
      rerankTokens += result.llmTokens();
      for (ChunkCandidate candidate : result.candidates()) {
        bestByChunk.merge(
            candidate.chunkId(),
            toMatch(candidate),
            (existing, incoming) -> existing.score() >= incoming.score() ? existing : incoming);
      }
    }
    return new DocumentChunkMatches(
        List.copyOf(bestByChunk.values()), embedded.totalTokens(), rerankTokens);
  }

  private DocumentChunkMatch toMatch(ChunkCandidate candidate) {
    return new DocumentChunkMatch(
        candidate.chunkId(), candidate.chunkIndex(), candidate.content(), candidate.score());
  }

  private RetrievedChunks toRetrievedChunks(HybridSearchResult result) {
    List<RetrievedChunk> chunks =
        result.candidates().stream()
            .map(
                candidate ->
                    new RetrievedChunk(
                        candidate.content(),
                        candidate.documentTitle(),
                        candidate.score(),
                        candidate.legislation(),
                        candidate.actCanonical(),
                        candidate.articleNumber(),
                        candidate.editionDate()))
            .toList();
    return new RetrievedChunks(chunks, result.llmTokens());
  }
}
