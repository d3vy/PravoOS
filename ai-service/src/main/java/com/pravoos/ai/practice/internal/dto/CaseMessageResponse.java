package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.entity.CaseMessage;
import com.pravoos.ai.shared.model.enums.MessageAuthorRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record CaseMessageResponse(
        UUID id,
        UUID authorUserId,
        MessageAuthorRole authorRole,
        String body,
        LocalDateTime createdAt
) {
    public static CaseMessageResponse from(CaseMessage message) {
        return new CaseMessageResponse(
                message.getId(),
                message.getAuthorUserId(),
                message.getAuthorRole(),
                message.getBody(),
                message.getCreatedAt());
    }
}
