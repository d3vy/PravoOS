package com.pravoos.ai.model.dto;

import com.pravoos.ai.model.entity.DocumentTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

public record TemplateResponse(
        UUID id,
        String name,
        String content,
        LocalDateTime createdAt
) {
    public static TemplateResponse from(DocumentTemplate template) {
        return new TemplateResponse(
                template.getId(),
                template.getName(),
                template.getContent(),
                template.getCreatedAt()
        );
    }
}
