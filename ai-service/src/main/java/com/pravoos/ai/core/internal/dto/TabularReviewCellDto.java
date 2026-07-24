package com.pravoos.ai.core.internal.dto;

import com.pravoos.ai.core.internal.model.entity.TabularReviewCell;
import com.pravoos.ai.shared.model.enums.ReviewAnswerConfidence;
import java.util.List;
import java.util.UUID;

public record TabularReviewCellDto(
    UUID documentId,
    int questionIndex,
    String answer,
    ReviewAnswerConfidence confidence,
    List<ReviewCitation> citations) {

  public static TabularReviewCellDto from(TabularReviewCell cell) {
    return new TabularReviewCellDto(
        cell.getDocumentId(),
        cell.getQuestionIndex(),
        cell.getAnswer(),
        cell.getConfidence(),
        cell.getCitations());
  }
}
