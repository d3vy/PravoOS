package com.pravoos.ai.practice.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefineDraftRequest(
        @NotBlank(message = "Укажите, что нужно улучшить")
        @Size(max = 1000, message = "Инструкция не должна превышать 1000 символов")
        String instruction,

        String selectedText
) {}
