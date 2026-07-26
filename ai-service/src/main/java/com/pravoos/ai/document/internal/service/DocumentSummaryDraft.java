package com.pravoos.ai.document.internal.service;

import java.util.List;

public record DocumentSummaryDraft(String summary, List<String> keyPoints) {

  public static final DocumentSummaryDraft EMPTY = new DocumentSummaryDraft("", List.of());

  public boolean isEmpty() {
    return summary.isBlank() && keyPoints.isEmpty();
  }
}
