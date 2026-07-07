package com.pravoos.ai.document.internal;

import com.pravoos.ai.document.api.DocumentRetrieval;
import com.pravoos.ai.document.api.RetrievedChunk;
import com.pravoos.ai.document.internal.repository.ChunkMatch;
import com.pravoos.ai.document.internal.repository.VectorSearchRepository;
import com.pravoos.ai.document.internal.service.EmbeddingService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
class DocumentRetrievalImpl implements DocumentRetrieval {

    private final EmbeddingService embeddingService;
    private final VectorSearchRepository vectorSearchRepository;

    DocumentRetrievalImpl(EmbeddingService embeddingService,
                          VectorSearchRepository vectorSearchRepository) {
        this.embeddingService = embeddingService;
        this.vectorSearchRepository = vectorSearchRepository;
    }

    @Override
    public List<RetrievedChunk> retrieveKnowledgeBase(String query, int topK) {
        float[] embedding = embeddingService.embed(query);
        return toRetrievedChunks(vectorSearchRepository.findTopKInKnowledgeBase(embedding, topK));
    }

    @Override
    public List<RetrievedChunk> retrieveForCase(String query, int topK, UUID caseId) {
        float[] embedding = embeddingService.embed(query);
        return toRetrievedChunks(vectorSearchRepository.findTopKForCase(embedding, topK, caseId));
    }

    private List<RetrievedChunk> toRetrievedChunks(List<ChunkMatch> matches) {
        return matches.stream()
                .map(match -> new RetrievedChunk(match.content(), match.documentTitle(), match.distance()))
                .toList();
    }
}
