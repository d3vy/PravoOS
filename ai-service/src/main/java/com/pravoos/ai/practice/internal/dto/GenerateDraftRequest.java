package com.pravoos.ai.practice.internal.dto;

import jakarta.validation.constraints.NotBlank;

public record GenerateDraftRequest(
    @NotBlank(message = "Тип документа обязателен") String draftType, String seedAnswer) {}
