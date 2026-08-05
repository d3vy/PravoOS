package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import com.pravoos.ai.shared.model.enums.CourtSystem;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PortalCaseDetailResponse(
    UUID id,
    String title,
    String description,
    CaseStatus status,
    String statusName,
    LocalDate filingDeadline,
    LocalDate nextHearingDate,
    CourtSystem courtSystem,
    String courtSystemName,
    String courtCaseNumber,
    String courtCardUrl,
    LocalDateTime createdAt,
    List<CaseHearingEventResponse> hearings) {
  public static PortalCaseDetailResponse from(
      Case caseEntity, List<CaseHearingEventResponse> hearings) {
    return new PortalCaseDetailResponse(
        caseEntity.getId(),
        caseEntity.getTitle(),
        caseEntity.getDescription(),
        caseEntity.getStatus(),
        caseEntity.getStatus().getDisplayName(),
        caseEntity.getFilingDeadline(),
        caseEntity.getNextHearingDate(),
        caseEntity.getCourtSystem(),
        caseEntity.getCourtSystem().getDisplayName(),
        caseEntity.getCourtCaseNumber(),
        caseEntity.getCourtSystem().cardUrl(caseEntity.getCourtCaseGuid()),
        caseEntity.getCreatedAt(),
        hearings);
  }
}
