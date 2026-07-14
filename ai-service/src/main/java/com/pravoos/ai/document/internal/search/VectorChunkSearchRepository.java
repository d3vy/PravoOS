package com.pravoos.ai.document.internal.search;

import com.pravoos.ai.shared.config.HybridSearchProperties;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class VectorChunkSearchRepository {

    private static final Logger log = LoggerFactory.getLogger(VectorChunkSearchRepository.class);

    private final double maxDistance;

    @PersistenceContext
    private EntityManager entityManager;

    public VectorChunkSearchRepository(HybridSearchProperties properties) {
        this.maxDistance = properties.maxDistance();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<ChunkCandidate> search(float[] queryEmbedding, int limit, ChunkSearchScope scope) {
        if (queryEmbedding == null || queryEmbedding.length == 0 || limit <= 0) {
            return List.of();
        }

        String sql = "SELECT " + ChunkSearchSql.SELECT_COLUMNS
                + ", (dc.embedding <=> CAST(:vec AS vector)) AS distance"
                + ChunkSearchSql.FROM_JOIN
                + "WHERE dc.embedding IS NOT NULL AND " + ChunkSearchSql.VISIBILITY_FILTER
                + ChunkSearchSql.scopeFilter(scope)
                + " AND (dc.embedding <=> CAST(:vec AS vector)) <= :maxDistance"
                + " ORDER BY distance LIMIT :limit";

        Query query = entityManager.createNativeQuery(sql)
                .setParameter("vec", toVectorLiteral(queryEmbedding))
                .setParameter("maxDistance", maxDistance)
                .setParameter("limit", limit);
        ChunkSearchSql.bindScope(query, scope);

        List<Object[]> rows = query.getResultList();
        List<ChunkCandidate> candidates = rows.stream()
                .map(row -> ChunkSearchSql.toCandidate(row, distance -> 1.0 - distance))
                .toList();

        log.debug("Vector search returned {} candidate(s) within distance {}", candidates.size(), maxDistance);
        return candidates;
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder literal = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(embedding[i]);
        }
        return literal.append(']').toString();
    }
}
