package com.pravoos.ai.document.internal.search;

import com.pravoos.ai.document.api.SearchActor;
import jakarta.persistence.Query;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class ChunkSearchSql {

  static final String TEXT_SEARCH_CONFIG = "russian";

  static final String SELECT_COLUMNS =
      "dc.id, dc.chunk_index, dc.content, d.title, d.document_kind, d.act_canonical, d.article_number, d.edition_date";

  static final String FROM_JOIN =
      " FROM document_chunks dc JOIN documents d ON d.id = dc.document_id ";

  static final String VISIBILITY_FILTER =
      " d.deleted_at IS NULL AND d.superseded = FALSE AND d.document_kind <> 'CHAT_ATTACHMENT' ";

  static final String CASE_VISIBLE_TO_ACTOR =
      "EXISTS (SELECT 1 FROM cases c WHERE c.id = d.case_id AND c.deleted_at IS NULL"
          + " AND (c.lawyer_id = CAST(:actorUserId AS uuid)"
          + " OR c.org_id = ANY (CAST(:actorOrgIds AS uuid[])))) ";

  private static final int SCORE_COLUMN = 8;

  private ChunkSearchSql() {}

  static String scopeFilter(ChunkSearchScope scope) {
    if (scope.documentScoped()) {
      return " AND d.id = CAST(:documentId AS uuid) AND (d.case_id IS NULL OR "
          + CASE_VISIBLE_TO_ACTOR
          + ") ";
    }
    if (scope.caseScoped()) {
      return " AND (d.case_id IS NULL OR (d.case_id = CAST(:caseId AS uuid) AND "
          + CASE_VISIBLE_TO_ACTOR
          + ")) ";
    }
    return " AND d.case_id IS NULL ";
  }

  static void bindScope(Query query, ChunkSearchScope scope) {
    if (scope.documentScoped()) {
      query.setParameter("documentId", scope.documentId().toString());
    } else if (scope.caseScoped()) {
      query.setParameter("caseId", scope.caseId().toString());
    } else {
      return;
    }
    SearchActor actor = scope.actor();
    query.setParameter("actorUserId", actor.userId().toString());
    query.setParameter("actorOrgIds", toUuidArrayLiteral(actor.orgIds()));
  }

  private static String toUuidArrayLiteral(List<UUID> orgIds) {
    StringBuilder literal = new StringBuilder("{");
    for (int index = 0; index < orgIds.size(); index++) {
      if (index > 0) {
        literal.append(',');
      }
      literal.append(orgIds.get(index));
    }
    return literal.append('}').toString();
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
