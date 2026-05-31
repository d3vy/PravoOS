package com.pravoos.ai.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public class VectorSearchRepository {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchRepository.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<ChunkMatch> findTopKInKnowledgeBase(float[] queryEmbedding, int k) {
        return search(
                "SELECT dc.content, d.title FROM document_chunks dc " +
                "JOIN documents d ON d.id = dc.document_id " +
                "WHERE dc.embedding IS NOT NULL AND d.case_id IS NULL " +
                "ORDER BY dc.embedding <=> CAST(:vec AS vector) " +
                "LIMIT :k",
                queryEmbedding, k, null);
    }

    @Transactional(readOnly = true)
    public List<ChunkMatch> findTopKForCase(float[] queryEmbedding, int k, UUID caseId) {
        return search(
                "SELECT dc.content, d.title FROM document_chunks dc " +
                "JOIN documents d ON d.id = dc.document_id " +
                "WHERE dc.embedding IS NOT NULL AND (d.case_id = CAST(:caseId AS uuid) OR d.case_id IS NULL) " +
                "ORDER BY dc.embedding <=> CAST(:vec AS vector) " +
                "LIMIT :k",
                queryEmbedding, k, caseId);
    }

    @SuppressWarnings("unchecked")
    private List<ChunkMatch> search(String sql, float[] queryEmbedding, int k, UUID caseId) {
        var query = entityManager.createNativeQuery(sql)
                .setParameter("vec", toVectorString(queryEmbedding))
                .setParameter("k", k);
        if (caseId != null) {
            query.setParameter("caseId", caseId.toString());
        }

        List<Object[]> rows = query.getResultList();
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
