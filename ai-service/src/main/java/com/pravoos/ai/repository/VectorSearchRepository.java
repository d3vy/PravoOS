package com.pravoos.ai.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class VectorSearchRepository {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchRepository.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<ChunkMatch> findTopKBySimilarity(float[] queryEmbedding, int k) {
        String vectorStr = toVectorString(queryEmbedding);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(
                        "SELECT dc.content, d.title FROM document_chunks dc " +
                        "JOIN documents d ON d.id = dc.document_id " +
                        "WHERE dc.embedding IS NOT NULL " +
                        "ORDER BY dc.embedding <=> CAST(:vec AS vector) " +
                        "LIMIT :k"
                )
                .setParameter("vec", vectorStr)
                .setParameter("k", k)
                .getResultList();

        List<ChunkMatch> matches = rows.stream()
                .map(row -> new ChunkMatch((String) row[0], (String) row[1]))
                .toList();

        log.debug("Vector search returned {} chunk(s) for top-{} query", matches.size(), k);
        return matches;
    }

    private String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        return sb.append("]").toString();
    }
}
