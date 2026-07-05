package com.pravoos.ai.core.internal.dto;

import com.pravoos.ai.core.internal.model.entity.ContractReview;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ContractReviewDto(
        UUID id,
        UUID caseId,
        UUID documentId,
        String documentTitle,
        String summary,
        short riskScore,
        int highRiskCount,
        List<ContractRisk> findings,
        LocalDateTime createdAt
) {
    public static ContractReviewDto from(ContractReview review) {
        return new ContractReviewDto(
                review.getId(),
                review.getCaseId(),
                review.getDocumentId(),
                review.getDocumentTitle(),
                review.getSummary(),
                review.getRiskScore(),
                review.getHighRiskCount(),
                review.getFindings() != null ? review.getFindings() : List.of(),
                review.getCreatedAt()
        );
    }
}
