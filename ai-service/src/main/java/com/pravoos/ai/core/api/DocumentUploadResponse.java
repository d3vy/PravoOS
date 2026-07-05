package com.pravoos.ai.core.api;

import com.pravoos.ai.shared.model.enums.DocumentStatus;

import java.util.UUID;

public record DocumentUploadResponse(
        UUID id,
        String title,
        String fileName,
        DocumentStatus status
) {}
