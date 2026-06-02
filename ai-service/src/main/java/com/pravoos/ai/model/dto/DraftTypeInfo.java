package com.pravoos.ai.model.dto;

import com.pravoos.ai.model.enums.DraftType;

public record DraftTypeInfo(
        String id,
        String displayName
) {
    public static DraftTypeInfo from(DraftType type) {
        return new DraftTypeInfo(type.name(), type.displayName());
    }
}
