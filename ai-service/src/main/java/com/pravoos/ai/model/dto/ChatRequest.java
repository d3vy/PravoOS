package com.pravoos.ai.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ChatRequest(
        String conversationId,
        @NotBlank @Size(max = 4000) String message,
        List<UUID> attachedDocumentIds
) {}
