package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.shared.model.enums.DraftType;

public record DraftTypeInfo(
        String id,
        String displayName
) {
    public static DraftTypeInfo from(DraftType type) {
        return new DraftTypeInfo(type.name(), type.displayName());
    }
}
