package com.pravoos.ai.core.internal.dto;

import com.pravoos.ai.core.internal.model.entity.TabularReviewDocument;
import com.pravoos.ai.shared.model.enums.TabularReviewStatus;
import java.util.UUID;

public record TabularReviewDocumentDto(
    UUID documentId,
    String documentTitle,
    int position,
    TabularReviewStatus status,
    String errorMessage) {

  public static TabularReviewDocumentDto from(TabularReviewDocument document) {
    return new TabularReviewDocumentDto(
        document.getDocumentId(),
        document.getDocumentTitle(),
        document.getPosition(),
        document.getStatus(),
        document.getErrorMessage());
  }
}
