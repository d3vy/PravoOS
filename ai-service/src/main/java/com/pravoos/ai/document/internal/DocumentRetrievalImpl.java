package com.pravoos.ai.document.internal;

import com.pravoos.ai.document.api.DocumentChunkMatch;
import com.pravoos.ai.document.api.DocumentRetrieval;
import com.pravoos.ai.document.api.RetrievedChunk;
import com.pravoos.ai.document.internal.search.ChunkCandidate;
import com.pravoos.ai.document.internal.search.ChunkSearchScope;
import com.pravoos.ai.document.internal.search.HybridSearchService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class DocumentRetrievalImpl implements DocumentRetrieval {

  private final HybridSearchService hybridSearchService;

  DocumentRetrievalImpl(HybridSearchService hybridSearchService) {
    this.hybridSearchService = hybridSearchService;
  }

  @Override
  public List<RetrievedChunk> retrieveKnowledgeBase(String query, int topK) {
    return toRetrievedChunks(
        hybridSearchService.search(query, topK, ChunkSearchScope.knowledgeBase()));
  }

  @Override
  public List<RetrievedChunk> retrieveForCase(String query, int topK, UUID caseId) {
    return toRetrievedChunks(
        hybridSearchService.search(query, topK, ChunkSearchScope.forCase(caseId)));
  }

  @Override
  public List<DocumentChunkMatch> retrieveInDocument(String query, int topK, UUID documentId) {
    return hybridSearchService
        .search(query, topK, ChunkSearchScope.forDocument(documentId))
        .stream()
        .map(
            candidate ->
                new DocumentChunkMatch(
                    candidate.chunkId(),
                    candidate.chunkIndex(),
                    candidate.content(),
                    candidate.score()))
        .toList();
  }

  private List<RetrievedChunk> toRetrievedChunks(List<ChunkCandidate> candidates) {
    return candidates.stream()
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
  }
}
