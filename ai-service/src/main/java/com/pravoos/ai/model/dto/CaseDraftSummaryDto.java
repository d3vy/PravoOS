package com.pravoos.ai.model.dto;

import com.pravoos.ai.model.entity.CaseDraft;
import com.pravoos.ai.model.enums.DraftType;

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
        try {
            return DraftType.valueOf(draftTypeId).displayName();
        } catch (IllegalArgumentException ex) {
            return draftTypeId;
        }
    }
}
