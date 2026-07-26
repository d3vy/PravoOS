package com.pravoos.ai.core.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ChatRequest(
    String conversationId,
    @NotBlank @Size(max = 4000) String message,
    @Size(max = 10, message = "Не более 10 вложенных документов на сообщение")
        List<UUID> attachedDocumentIds,
    UUID caseId,
    UUID documentId) {}
