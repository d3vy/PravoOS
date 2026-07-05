package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.entity.CaseDraft;
import com.pravoos.ai.shared.model.enums.DraftType;

import java.time.LocalDateTime;
import java.util.UUID;

public record CaseDraftSummaryDto(
        UUID id,
        UUID caseId,
        String draftType,
        String draftTypeName,
        String title,
        LocalDateTime createdAt
) {
    public static CaseDraftSummaryDto from(CaseDraft draft) {
        String draftTypeName = resolveDraftTypeName(draft.getDraftType());
        return new CaseDraftSummaryDto(
                draft.getId(),
                draft.getCaseId(),
                draft.getDraftType(),
                draftTypeName,
                draft.getTitle(),
                draft.getCreatedAt()
        );
    }

    private static String resolveDraftTypeName(String draftTypeId) {
        if ("TEMPLATE".equals(draftTypeId)) {
            return "Шаблон";
        }
        try {
            return DraftType.valueOf(draftTypeId).displayName();
        } catch (IllegalArgumentException ex) {
            return draftTypeId;
        }
    }
}
