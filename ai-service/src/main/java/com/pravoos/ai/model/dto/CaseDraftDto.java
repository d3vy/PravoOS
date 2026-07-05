package com.pravoos.ai.model.dto;

import com.pravoos.ai.practice.internal.model.entity.CaseDraft;
import com.pravoos.ai.model.enums.DraftType;

import java.time.LocalDateTime;
import java.util.UUID;

public record CaseDraftDto(
        UUID id,
        UUID caseId,
        String draftType,
        String draftTypeName,
        String title,
        String content,
        LocalDateTime createdAt
) {
    public static CaseDraftDto from(CaseDraft draft) {
        String draftTypeName = resolveDraftTypeName(draft.getDraftType());
        return new CaseDraftDto(
                draft.getId(),
                draft.getCaseId(),
                draft.getDraftType(),
                draftTypeName,
                draft.getTitle(),
                draft.getContent(),
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
