package com.pravoos.ai.model.dto;

import com.pravoos.ai.model.entity.CaseMessage;
import com.pravoos.ai.model.enums.MessageAuthorRole;

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
