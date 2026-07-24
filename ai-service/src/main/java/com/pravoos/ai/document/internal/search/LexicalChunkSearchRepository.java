package com.pravoos.ai.document.internal.search;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class LexicalChunkSearchRepository {

  private static final Logger log = LoggerFactory.getLogger(LexicalChunkSearchRepository.class);

  private static final String TS_QUERY =
      "websearch_to_tsquery('" + ChunkSearchSql.TEXT_SEARCH_CONFIG + "', :q)";

  @PersistenceContext private EntityManager entityManager;

  @Transactional(readOnly = true)
  @SuppressWarnings("unchecked")
  public List<ChunkCandidate> search(String queryText, int limit, ChunkSearchScope scope) {
    if (queryText == null || queryText.isBlank() || limit <= 0) {
      return List.of();
    }

    String sql =
        "SELECT "
            + ChunkSearchSql.SELECT_COLUMNS
            + ", ts_rank_cd(dc.content_tsv, "
            + TS_QUERY
            + ") AS rank"
            + ChunkSearchSql.FROM_JOIN
            + "WHERE dc.content_tsv @@ "
            + TS_QUERY
            + " AND "
            + ChunkSearchSql.VISIBILITY_FILTER
            + ChunkSearchSql.scopeFilter(scope)
            + " ORDER BY rank DESC LIMIT :limit";

    Query query =
        entityManager
            .createNativeQuery(sql)
            .setParameter("q", queryText)
            .setParameter("limit", limit);
    ChunkSearchSql.bindScope(query, scope);

    List<Object[]> rows = query.getResultList();
    List<ChunkCandidate> candidates =
        rows.stream().map(row -> ChunkSearchSql.toCandidate(row, rank -> rank)).toList();

    log.debug("Lexical search returned {} candidate(s)", candidates.size());
    return candidates;
  }
}
