package com.pravoos.ai.document.internal.search;

import com.pravoos.ai.document.internal.service.EmbeddingService;
import com.pravoos.ai.shared.config.HybridSearchProperties;
import jakarta.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public HybridSearchService(EmbeddingService embeddingService,
                               VectorChunkSearchRepository vectorSearchRepository,
                               LexicalChunkSearchRepository lexicalSearchRepository,
                               Reranker reranker,
                               HybridSearchProperties properties) {
        this.embeddingService = embeddingService;
        this.vectorSearchRepository = vectorSearchRepository;
        this.lexicalSearchRepository = lexicalSearchRepository;
        this.reranker = reranker;
        this.properties = properties;
    }

    public List<ChunkCandidate> search(String query, int topK, ChunkSearchScope scope) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }

        int candidateLimit = properties.candidateLimit(topK);
        List<ChunkCandidate> vectorCandidates =
                vectorSearchRepository.search(embeddingService.embed(query), candidateLimit, scope);
        List<ChunkCandidate> lexicalCandidates = lexicalCandidates(query, candidateLimit, scope);

        List<ChunkCandidate> fused = ReciprocalRankFusion.fuse(
                List.of(new RankedSource(VECTOR_SOURCE, vectorCandidates, properties.vectorWeight()),
                        new RankedSource(LEXICAL_SOURCE, lexicalCandidates, properties.lexicalWeight())),
                properties.rrfK(),
                properties.legislationBoost());

        if (fused.isEmpty()) {
            return List.of();
        }

        int rerankInputSize = Math.min(fused.size(), properties.rerankInputLimit(topK));
        List<ChunkCandidate> rerankInput = fused.subList(0, rerankInputSize);

        log.debug("Hybrid search: {} vector + {} lexical → {} fused → rerank top-{} → return top-{}",
                vectorCandidates.size(), lexicalCandidates.size(), fused.size(), rerankInputSize, topK);

        return reranker.rerank(query, rerankInput, topK);
    }

    private List<ChunkCandidate> lexicalCandidates(String query, int candidateLimit, ChunkSearchScope scope) {
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
