package com.pravoos.ai.core.internal.dto;

import com.pravoos.ai.core.internal.model.entity.DocumentComparison;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DocumentComparisonDto(
        UUID id,
        UUID caseId,
        UUID baseDocumentId,
        UUID revisedDocumentId,
        String baseDocumentTitle,
        String revisedDocumentTitle,
        String summary,
        short riskScore,
        int changeCount,
        int highRiskCount,
        List<DiffChange> changes,
        LocalDateTime createdAt
) {
    public static DocumentComparisonDto from(DocumentComparison comparison) {
        return new DocumentComparisonDto(
                comparison.getId(),
                comparison.getCaseId(),
                comparison.getBaseDocumentId(),
                comparison.getRevisedDocumentId(),
                comparison.getBaseDocumentTitle(),
                comparison.getRevisedDocumentTitle(),
                comparison.getSummary(),
                comparison.getRiskScore(),
                comparison.getChangeCount(),
                comparison.getHighRiskCount(),
                comparison.getChanges() != null ? comparison.getChanges() : List.of(),
                comparison.getCreatedAt()
        );
    }
}
