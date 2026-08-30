package com.pravoos.ai.core.api;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.DocumentSummaryView;
import com.pravoos.ai.shared.exception.CaseNotFoundException;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import com.pravoos.ai.shared.model.enums.DocumentKind;
import com.pravoos.ai.shared.model.enums.DocumentStatus;
import com.pravoos.ai.shared.model.enums.DocumentSummaryStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentAccessGuardTest {

  @Mock private DocumentAccess documentAccess;
  @Mock private CaseAccessProvider caseAccessProvider;

  private final UUID documentId = UUID.randomUUID();
  private final UUID lawyerId = UUID.randomUUID();
  private final List<UUID> orgIds = List.of(UUID.randomUUID());

  private DocumentAccessGuard guard() {
    return new DocumentAccessGuard(documentAccess, caseAccessProvider);
  }

  private void stubDocument(UUID caseId, UUID uploadedBy, DocumentKind kind) {
    when(documentAccess.summaryFor(documentId))
        .thenReturn(
            new DocumentSummaryView(
                documentId,
                caseId,
                uploadedBy,
                "Документ",
                kind,
                DocumentStatus.READY,
                DocumentSummaryStatus.NONE,
                null,
                List.of(),
                null));
  }

  @Test
  void delegatesCaseBoundDocumentToCaseVisibility() {
    UUID caseId = UUID.randomUUID();
    stubDocument(caseId, UUID.randomUUID(), DocumentKind.GENERAL);

    guard().requireVisible(documentId, lawyerId, orgIds);

    verify(caseAccessProvider).assertCaseVisible(caseId, lawyerId, orgIds);
  }

  @Test
  void propagatesCaseAccessDenial() {
    UUID caseId = UUID.randomUUID();
    stubDocument(caseId, UUID.randomUUID(), DocumentKind.GENERAL);
    doThrow(new CaseNotFoundException(caseId))
        .when(caseAccessProvider)
        .assertCaseVisible(eq(caseId), eq(lawyerId), anyList());

    assertThatThrownBy(() -> guard().requireVisible(documentId, lawyerId, orgIds))
        .isInstanceOf(CaseNotFoundException.class);
  }

  @Test
  void allowsKnowledgeBaseDocumentToAnyLawyer() {
    stubDocument(null, UUID.randomUUID(), DocumentKind.GENERAL);

    assertThatCode(() -> guard().requireVisible(documentId, lawyerId, orgIds))
        .doesNotThrowAnyException();
    verify(caseAccessProvider, never()).assertCaseVisible(any(), any(), anyList());
  }

  @Test
  void hidesChatAttachmentOfAnotherUser() {
    stubDocument(null, UUID.randomUUID(), DocumentKind.CHAT_ATTACHMENT);

    assertThatThrownBy(() -> guard().requireVisible(documentId, lawyerId, orgIds))
        .isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void allowsOwnChatAttachment() {
    stubDocument(null, lawyerId, DocumentKind.CHAT_ATTACHMENT);

    assertThatCode(() -> guard().requireVisible(documentId, lawyerId, orgIds))
        .doesNotThrowAnyException();
  }
}
