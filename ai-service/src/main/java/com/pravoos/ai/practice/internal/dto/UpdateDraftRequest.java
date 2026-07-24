package com.pravoos.ai.practice.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDraftRequest(
    @NotBlank(message = "Содержимое документа обязательно") String content,
    @Size(max = 500, message = "Описание правки не должно превышать 500 символов") String note) {}
