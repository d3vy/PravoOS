package com.pravoos.ai.document.internal.search;

import jakarta.persistence.Query;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

final class ChunkSearchSql {

  static final String TEXT_SEARCH_CONFIG = "russian";

  static final String SELECT_COLUMNS =
      "dc.id, dc.chunk_index, dc.content, d.title, d.document_kind, d.act_canonical, d.article_number, d.edition_date";

  static final String FROM_JOIN =
      " FROM document_chunks dc JOIN documents d ON d.id = dc.document_id ";

  static final String VISIBILITY_FILTER =
      " d.deleted_at IS NULL AND d.superseded = FALSE AND d.document_kind <> 'CHAT_ATTACHMENT' ";

  private static final int SCORE_COLUMN = 8;

  private ChunkSearchSql() {}

  static String scopeFilter(ChunkSearchScope scope) {
    if (scope.documentScoped()) {
      return " AND d.id = CAST(:documentId AS uuid) ";
    }
    return scope.caseScoped()
        ? " AND (d.case_id = CAST(:caseId AS uuid) OR d.case_id IS NULL) "
        : " AND d.case_id IS NULL ";
  }

  static void bindScope(Query query, ChunkSearchScope scope) {
    if (scope.documentScoped()) {
      query.setParameter("documentId", scope.documentId().toString());
    } else if (scope.caseScoped()) {
      query.setParameter("caseId", scope.caseId().toString());
    }
  }

  static ChunkCandidate toCandidate(Object[] row, ScoreMapper scoreMapper) {
    return new ChunkCandidate(
        toUuid(row[0]),
        ((Number) row[1]).intValue(),
        (String) row[2],
        (String) row[3],
        "LEGISLATION".equals(row[4]),
        (String) row[5],
        (String) row[6],
        toLocalDate(row[7]),
        scoreMapper.toScore(((Number) row[SCORE_COLUMN]).doubleValue()));
  }

  @FunctionalInterface
  interface ScoreMapper {
    double toScore(double rawValue);
  }

  private static UUID toUuid(Object value) {
    if (value instanceof UUID uuid) {
      return uuid;
    }
    return UUID.fromString(String.valueOf(value));
  }

  private static LocalDate toLocalDate(Object value) {
    if (value instanceof Date sqlDate) {
      return sqlDate.toLocalDate();
    }
    if (value instanceof LocalDate localDate) {
      return localDate;
    }
    return null;
  }
}
