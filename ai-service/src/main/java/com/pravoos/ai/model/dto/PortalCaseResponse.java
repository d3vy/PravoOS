package com.pravoos.ai.model.dto;

import com.pravoos.ai.model.entity.Case;
import com.pravoos.ai.model.enums.CaseStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PortalCaseResponse(
        UUID id,
        String title,
        String description,
        CaseStatus status,
        String statusName,
        LocalDate filingDeadline,
        LocalDate nextHearingDate,
        LocalDateTime createdAt
) {
    public static PortalCaseResponse from(Case caseEntity) {
        return new PortalCaseResponse(
                caseEntity.getId(),
                caseEntity.getTitle(),
                caseEntity.getDescription(),
                caseEntity.getStatus(),
                caseEntity.getStatus().getDisplayName(),
                caseEntity.getFilingDeadline(),
                caseEntity.getNextHearingDate(),
                caseEntity.getCreatedAt());
    }
}
