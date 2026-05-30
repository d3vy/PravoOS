package com.pravoos.ai.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class VectorSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<String> findTopKContentBySimilarity(float[] queryEmbedding, int k) {
        String vectorStr = toVectorString(queryEmbedding);
        return entityManager.createNativeQuery(
                        "SELECT content FROM document_chunks " +
                        "WHERE embedding IS NOT NULL " +
                        "ORDER BY embedding <=> CAST(:vec AS vector) " +
                        "LIMIT :k"
                )
                .setParameter("vec", vectorStr)
                .setParameter("k", k)
                .getResultList();
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
