package com.pravoos.ai.document.internal.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.persistence.Query;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChunkSearchSqlTest {

  @Mock private Query query;

  @Test
  void scopeFilter_filtersByDocument_whenDocumentScoped() {
    ChunkSearchScope scope = ChunkSearchScope.forDocument(UUID.randomUUID());

    assertThat(ChunkSearchSql.scopeFilter(scope)).contains(":documentId");
  }

  @Test
  void scopeFilter_filtersByCaseOrKnowledgeBase_whenCaseScoped() {
    ChunkSearchScope scope = ChunkSearchScope.forCase(UUID.randomUUID());

    String filter = ChunkSearchSql.scopeFilter(scope);

    assertThat(filter).contains(":caseId").contains("IS NULL");
  }

  @Test
  void scopeFilter_restrictsToKnowledgeBase_whenUnscoped() {
    ChunkSearchScope scope = ChunkSearchScope.knowledgeBase();

    assertThat(ChunkSearchSql.scopeFilter(scope)).isEqualTo(" AND d.case_id IS NULL ");
  }

  @Test
  void bindScope_setsDocumentIdParameter_whenDocumentScoped() {
    UUID documentId = UUID.randomUUID();
    ChunkSearchSql.bindScope(query, ChunkSearchScope.forDocument(documentId));

    verify(query).setParameter("documentId", documentId.toString());
    verify(query, never()).setParameter("caseId", (Object) null);
  }

  @Test
  void bindScope_setsCaseIdParameter_whenCaseScoped() {
    UUID caseId = UUID.randomUUID();
    ChunkSearchSql.bindScope(query, ChunkSearchScope.forCase(caseId));

    verify(query).setParameter("caseId", caseId.toString());
  }

  @Test
  void bindScope_setsNoParameter_whenKnowledgeBaseScoped() {
    ChunkSearchSql.bindScope(query, ChunkSearchScope.knowledgeBase());

    verify(query, never()).setParameter("documentId", (Object) null);
  }

  @Test
  void toCandidate_mapsRowWithSqlDateAndLegislationKind() {
    UUID chunkId = UUID.randomUUID();
    Object[] row = {
      chunkId.toString(),
      3,
      "content",
      "title",
      "LEGISLATION",
      "ГК РФ",
      "15",
      Date.valueOf(LocalDate.of(2024, 1, 1)),
      0.75
    };

    ChunkCandidate candidate = ChunkSearchSql.toCandidate(row, raw -> raw * 2);

    assertThat(candidate.chunkId()).isEqualTo(chunkId);
    assertThat(candidate.chunkIndex()).isEqualTo(3);
    assertThat(candidate.content()).isEqualTo("content");
    assertThat(candidate.documentTitle()).isEqualTo("title");
    assertThat(candidate.legislation()).isTrue();
    assertThat(candidate.actCanonical()).isEqualTo("ГК РФ");
    assertThat(candidate.articleNumber()).isEqualTo("15");
    assertThat(candidate.editionDate()).isEqualTo(LocalDate.of(2024, 1, 1));
    assertThat(candidate.score()).isEqualTo(1.5);
  }

  @Test
  void toCandidate_mapsRowWithUuidObjectAndNonLegislationKind() {
    UUID chunkId = UUID.randomUUID();
    Object[] row = {chunkId, 0, "content", "title", "GENERAL", null, null, null, 0.5};

    ChunkCandidate candidate = ChunkSearchSql.toCandidate(row, raw -> raw);

    assertThat(candidate.chunkId()).isEqualTo(chunkId);
    assertThat(candidate.legislation()).isFalse();
    assertThat(candidate.editionDate()).isNull();
  }
}
