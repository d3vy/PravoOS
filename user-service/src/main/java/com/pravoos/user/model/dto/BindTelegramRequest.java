package com.pravoos.user.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BindTelegramRequest(
        @NotBlank String code,
        @NotNull Long chatId
) {}
