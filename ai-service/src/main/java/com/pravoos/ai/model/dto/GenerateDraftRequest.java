package com.pravoos.ai.model.dto;

import jakarta.validation.constraints.NotBlank;

public record GenerateDraftRequest(
        @NotBlank(message = "Тип документа обязателен")
        String draftType
) {}
