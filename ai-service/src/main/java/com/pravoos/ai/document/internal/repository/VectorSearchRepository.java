package com.pravoos.ai.document.internal.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public class VectorSearchRepository {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchRepository.class);

    private static final String SELECT =
            "SELECT dc.content, d.title, (dc.embedding <=> CAST(:vec AS vector)) AS distance, " +
            "d.document_kind, d.act_canonical, d.article_number, d.edition_date ";
    private static final String LEGISLATION_RANK =
            "(dc.embedding <=> CAST(:vec AS vector)) " +
            "- CASE WHEN d.document_kind = 'LEGISLATION' THEN :legislationBoost ELSE 0 END";

    private final double maxDistance;
    private final double legislationBoost;

    @PersistenceContext
    private EntityManager entityManager;

    public VectorSearchRepository(@Value("${document.max-distance:0.85}") double maxDistance,
                                  @Value("${document.legislation-boost:0.05}") double legislationBoost) {
        this.maxDistance = maxDistance;
        this.legislationBoost = legislationBoost;
    }

    @Transactional(readOnly = true)
    public List<ChunkMatch> findTopKInKnowledgeBase(float[] queryEmbedding, int k) {
        return search(
                SELECT +
                "FROM document_chunks dc " +
                "JOIN documents d ON d.id = dc.document_id " +
                "WHERE dc.embedding IS NOT NULL AND d.case_id IS NULL AND d.superseded = FALSE " +
                "AND d.document_kind <> 'CHAT_ATTACHMENT' " +
                "AND (dc.embedding <=> CAST(:vec AS vector)) <= :maxDistance " +
                "ORDER BY " + LEGISLATION_RANK + " " +
                "LIMIT :k",
                queryEmbedding, k, null);
    }

    @Transactional(readOnly = true)
    public List<ChunkMatch> findTopKForCase(float[] queryEmbedding, int k, UUID caseId) {
        return search(
                SELECT +
                "FROM document_chunks dc " +
                "JOIN documents d ON d.id = dc.document_id " +
                "WHERE dc.embedding IS NOT NULL AND d.superseded = FALSE " +
                "AND d.document_kind <> 'CHAT_ATTACHMENT' " +
                "AND (d.case_id = CAST(:caseId AS uuid) OR d.case_id IS NULL) " +
                "AND (dc.embedding <=> CAST(:vec AS vector)) <= :maxDistance " +
                "ORDER BY " + LEGISLATION_RANK + " " +
                "LIMIT :k",
                queryEmbedding, k, caseId);
    }

    @SuppressWarnings("unchecked")
    private List<ChunkMatch> search(String sql, float[] queryEmbedding, int k, UUID caseId) {
        var query = entityManager.createNativeQuery(sql)
                .setParameter("vec", toVectorString(queryEmbedding))
                .setParameter("maxDistance", maxDistance)
                .setParameter("legislationBoost", legislationBoost)
                .setParameter("k", k);
        if (caseId != null) {
            query.setParameter("caseId", caseId.toString());
        }

        List<Object[]> rows = query.getResultList();
        List<ChunkMatch> matches = rows.stream()
                .map(row -> new ChunkMatch(
                        (String) row[0],
                        (String) row[1],
                        ((Number) row[2]).doubleValue(),
                        "LEGISLATION".equals(row[3]),
                        (String) row[4],
                        (String) row[5],
                        toLocalDate(row[6])))
                .toList();

        log.debug("Vector search returned {} chunk(s) within distance {} for top-{} query",
                matches.size(), maxDistance, k);
        return matches;
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return null;
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
