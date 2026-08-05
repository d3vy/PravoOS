package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import com.pravoos.ai.shared.model.enums.CourtSystem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CaseResponse(
    UUID id,
    UUID ownerId,
    UUID orgId,
    String title,
    String description,
    UUID clientId,
    String clientName,
    CaseStatus status,
    String statusName,
    LocalDate filingDeadline,
    LocalDate nextHearingDate,
    LocalDate expiresAt,
    CourtSystem courtSystem,
    String courtSystemName,
    String courtCaseNumber,
    String courtCardUrl,
    BigDecimal defaultHourlyRate,
    LocalDateTime createdAt) {
  public static CaseResponse from(Case caseEntity, String clientName) {
    return new CaseResponse(
        caseEntity.getId(),
        caseEntity.getLawyerId(),
        caseEntity.getOrgId(),
        caseEntity.getTitle(),
        caseEntity.getDescription(),
        caseEntity.getClientId(),
        clientName,
        caseEntity.getStatus(),
        caseEntity.getStatus().getDisplayName(),
        caseEntity.getFilingDeadline(),
        caseEntity.getNextHearingDate(),
        caseEntity.getExpiresAt(),
        caseEntity.getCourtSystem(),
        caseEntity.getCourtSystem().getDisplayName(),
        caseEntity.getCourtCaseNumber(),
        caseEntity.getCourtSystem().cardUrl(caseEntity.getCourtCaseGuid()),
        caseEntity.getDefaultHourlyRate(),
        caseEntity.getCreatedAt());
  }
}
