package com.pravoos.ai.document.api;

import com.pravoos.ai.shared.model.enums.DocumentStatus;

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
