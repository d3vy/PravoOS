package com.pravoos.ai.model.dto;

import com.pravoos.ai.model.entity.Case;
import com.pravoos.ai.model.enums.CaseStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record CaseResponse(
        UUID id,
        String title,
        String description,
        UUID clientId,
        String clientName,
        CaseStatus status,
        String statusName,
        LocalDateTime createdAt
) {
    public static CaseResponse from(Case caseEntity, String clientName) {
        return new CaseResponse(
                caseEntity.getId(),
                caseEntity.getTitle(),
                caseEntity.getDescription(),
                caseEntity.getClientId(),
                clientName,
                caseEntity.getStatus(),
                caseEntity.getStatus().getDisplayName(),
                caseEntity.getCreatedAt()
        );
    }
}
