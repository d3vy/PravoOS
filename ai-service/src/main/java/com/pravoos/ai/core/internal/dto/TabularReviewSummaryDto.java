package com.pravoos.ai.core.internal.dto;

import com.pravoos.ai.core.internal.model.entity.TabularReview;
import com.pravoos.ai.shared.model.enums.TabularReviewStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record TabularReviewSummaryDto(
        UUID id,
        UUID caseId,
        String title,
        TabularReviewStatus status,
        int documentCount,
        int questionCount,
        LocalDateTime createdAt,
        LocalDateTime completedAt) {

    public static TabularReviewSummaryDto from(TabularReview review) {
        return new TabularReviewSummaryDto(
                review.getId(),
                review.getCaseId(),
                review.getTitle(),
                review.getStatus(),
                review.getDocumentCount(),
                review.getQuestionCount(),
                review.getCreatedAt(),
                review.getCompletedAt());
    }
}
