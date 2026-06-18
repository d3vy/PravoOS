package com.pravoos.ai.model.dto;

import com.pravoos.ai.model.enums.CaseStatus;

import java.util.List;
import java.util.UUID;

public record GlobalSearchResponse(
        List<CaseHit> cases,
        List<ConversationHit> conversations,
        List<DocumentHit> documents
) {
    public record CaseHit(
            UUID id,
            String title,
            CaseStatus status,
            String statusName,
            String clientName
    ) {}

    public record ConversationHit(
            String id,
            String title
    ) {}

    public record DocumentHit(
            UUID id,
            String title,
            String fileName,
            UUID caseId
    ) {}
}
