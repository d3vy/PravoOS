package com.pravoos.ai.core.internal.dto;

import com.pravoos.ai.document.api.DocumentSummaryView;
import com.pravoos.ai.shared.model.enums.DocumentStatus;
import com.pravoos.ai.shared.model.enums.DocumentSummaryStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DocumentInsightResponse(
    UUID documentId,
    String title,
    DocumentStatus status,
    DocumentSummaryStatus summaryStatus,
    String summary,
    List<String> keyPoints,
    LocalDateTime generatedAt) {

  public static DocumentInsightResponse from(DocumentSummaryView view) {
    return new DocumentInsightResponse(
        view.id(),
        view.title(),
        view.status(),
        view.summaryStatus(),
        view.summary(),
        view.keyPoints(),
        view.generatedAt());
  }
}
