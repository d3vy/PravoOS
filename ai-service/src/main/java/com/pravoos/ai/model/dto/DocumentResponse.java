package com.pravoos.ai.model.dto;

import com.pravoos.ai.model.enums.DocumentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String title,
        String fileName,
        String fileType,
        DocumentStatus status,
        LocalDateTime uploadedAt,
        boolean visibleToClient
) {}
