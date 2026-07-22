package com.pravoos.ai.core.internal.dto;

import com.pravoos.ai.core.internal.model.entity.TabularReview;
import com.pravoos.ai.core.internal.model.entity.TabularReviewCell;
import com.pravoos.ai.core.internal.model.entity.TabularReviewDocument;
import com.pravoos.ai.shared.model.enums.TabularReviewStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TabularReviewDto(
        UUID id,
        UUID caseId,
        String title,
        TabularReviewStatus status,
        List<String> questions,
        List<TabularReviewDocumentDto> documents,
        List<TabularReviewCellDto> cells,
        int filledCells,
        int totalCells,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime completedAt) {

    public static TabularReviewDto from(TabularReview review,
                                        List<TabularReviewDocument> documents,
                                        List<TabularReviewCell> cells) {
        return new TabularReviewDto(
                review.getId(),
                review.getCaseId(),
                review.getTitle(),
                review.getStatus(),
                review.getQuestions(),
                documents.stream().map(TabularReviewDocumentDto::from).toList(),
                cells.stream().map(TabularReviewCellDto::from).toList(),
                cells.size(),
                review.getDocumentCount() * review.getQuestionCount(),
                review.getErrorMessage(),
                review.getCreatedAt(),
                review.getCompletedAt());
    }
}
