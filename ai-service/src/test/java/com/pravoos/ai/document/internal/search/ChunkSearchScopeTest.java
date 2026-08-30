package com.pravoos.ai.document.internal.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pravoos.ai.document.api.SearchActor;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChunkSearchScopeTest {

  private final SearchActor actor = SearchActor.of(UUID.randomUUID(), List.of(UUID.randomUUID()));

  @Test
  void knowledgeBase_hasNoCaseOrDocument() {
    ChunkSearchScope scope = ChunkSearchScope.knowledgeBase();

    assertThat(scope.caseScoped()).isFalse();
    assertThat(scope.documentScoped()).isFalse();
    assertThat(scope.actor()).isNull();
  }

  @Test
  void forCase_setsCaseIdAndActor() {
    UUID caseId = UUID.randomUUID();
    ChunkSearchScope scope = ChunkSearchScope.forCase(caseId, actor);

    assertThat(scope.caseScoped()).isTrue();
    assertThat(scope.documentScoped()).isFalse();
    assertThat(scope.caseId()).isEqualTo(caseId);
    assertThat(scope.actor()).isEqualTo(actor);
  }

  @Test
  void forCase_throwsIllegalArgumentException_whenCaseIdNull() {
    assertThatThrownBy(() -> ChunkSearchScope.forCase(null, actor))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void forCase_throwsIllegalArgumentException_whenActorNull() {
    assertThatThrownBy(() -> ChunkSearchScope.forCase(UUID.randomUUID(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void forDocument_setsDocumentIdAndActor() {
    UUID documentId = UUID.randomUUID();
    ChunkSearchScope scope = ChunkSearchScope.forDocument(documentId, actor);

    assertThat(scope.documentScoped()).isTrue();
    assertThat(scope.caseScoped()).isFalse();
    assertThat(scope.documentId()).isEqualTo(documentId);
    assertThat(scope.actor()).isEqualTo(actor);
  }

  @Test
  void forDocument_throwsIllegalArgumentException_whenDocumentIdNull() {
    assertThatThrownBy(() -> ChunkSearchScope.forDocument(null, actor))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void forDocument_throwsIllegalArgumentException_whenActorNull() {
    assertThatThrownBy(() -> ChunkSearchScope.forDocument(UUID.randomUUID(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
