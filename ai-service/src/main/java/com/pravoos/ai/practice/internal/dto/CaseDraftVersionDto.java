package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.entity.CaseDraftVersion;

import java.time.LocalDateTime;
import java.util.UUID;

public record CaseDraftVersionDto(
        UUID id,
        int versionNo,
        String note,
        String content,
        LocalDateTime createdAt
) {
    public static CaseDraftVersionDto from(CaseDraftVersion version) {
        return new CaseDraftVersionDto(
                version.getId(),
                version.getVersionNo(),
                version.getNote(),
                version.getContent(),
                version.getCreatedAt()
        );
    }
}
