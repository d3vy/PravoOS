package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.shared.model.enums.CaseStatus;
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
    String arbitrCaseNumber,
    String arbitrCardUrl,
    LocalDateTime createdAt,
    List<CaseHearingEventResponse> hearings) {
  private static final String KAD_CARD_BASE_URL = "https://kad.arbitr.ru/Card/";

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
        caseEntity.getArbitrCaseNumber(),
        caseEntity.getArbitrCaseGuid() == null
            ? null
            : KAD_CARD_BASE_URL + caseEntity.getArbitrCaseGuid(),
        caseEntity.getCreatedAt(),
        hearings);
  }
}
