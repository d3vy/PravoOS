package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.api.DocumentAccessGuard;
import com.pravoos.ai.core.internal.dto.DocumentInsightResponse;
import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.DocumentSummaryView;
import com.pravoos.ai.shared.model.enums.DocumentSummaryStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentInsightServiceTest {

  @Mock private DocumentAccess documentAccess;
  @Mock private DocumentAccessGuard documentAccessGuard;

  private DocumentInsightService service;

  private final UUID documentId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new DocumentInsightService(documentAccess, documentAccessGuard);
  }

  private final UUID userId = UUID.randomUUID();

  private DocumentSummaryView view() {
    return new DocumentSummaryView(
        documentId,
        null,
        userId,
        "Договор.pdf",
        null,
        null,
        DocumentSummaryStatus.READY,
        "Краткое содержание",
        List.of("пункт 1"),
        null);
  }

  @Test
  void summaryChecksVisibilityAndMapsResponse() {
    when(documentAccessGuard.requireVisible(documentId, userId, List.of())).thenReturn(view());

    DocumentInsightResponse response = service.summary(documentId, userId, List.of());

    assertThat(response.title()).isEqualTo("Договор.pdf");
    assertThat(response.summary()).isEqualTo("Краткое содержание");
  }

  @Test
  void regenerateChecksVisibilityBeforeRegenerating() {
    when(documentAccessGuard.requireVisible(documentId, userId, List.of())).thenReturn(view());
    when(documentAccess.regenerateSummary(documentId, userId)).thenReturn(view());

    DocumentInsightResponse response = service.regenerate(documentId, userId, List.of());

    assertThat(response.documentId()).isEqualTo(documentId);
    verify(documentAccessGuard).requireVisible(documentId, userId, List.of());
    verify(documentAccess).regenerateSummary(documentId, userId);
  }
}
