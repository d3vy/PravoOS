package com.pravoos.ai.document.internal.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChunkSearchScopeTest {

  @Test
  void knowledgeBase_hasNoCaseOrDocument() {
    ChunkSearchScope scope = ChunkSearchScope.knowledgeBase();

    assertThat(scope.caseScoped()).isFalse();
    assertThat(scope.documentScoped()).isFalse();
  }

  @Test
  void forCase_setsCaseId() {
    UUID caseId = UUID.randomUUID();
    ChunkSearchScope scope = ChunkSearchScope.forCase(caseId);

    assertThat(scope.caseScoped()).isTrue();
    assertThat(scope.documentScoped()).isFalse();
    assertThat(scope.caseId()).isEqualTo(caseId);
  }

  @Test
  void forCase_throwsIllegalArgumentException_whenCaseIdNull() {
    assertThatThrownBy(() -> ChunkSearchScope.forCase(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void forDocument_setsDocumentId() {
    UUID documentId = UUID.randomUUID();
    ChunkSearchScope scope = ChunkSearchScope.forDocument(documentId);

    assertThat(scope.documentScoped()).isTrue();
    assertThat(scope.caseScoped()).isFalse();
    assertThat(scope.documentId()).isEqualTo(documentId);
  }

  @Test
  void forDocument_throwsIllegalArgumentException_whenDocumentIdNull() {
    assertThatThrownBy(() -> ChunkSearchScope.forDocument(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
