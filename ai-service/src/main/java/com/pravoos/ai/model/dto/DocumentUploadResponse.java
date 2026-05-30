package com.pravoos.ai.model.dto;

import com.pravoos.ai.model.enums.DocumentStatus;

import java.util.UUID;

public record DocumentUploadResponse(
        UUID id,
        String title,
        String fileName,
        DocumentStatus status
) {}
