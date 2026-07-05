package com.pravoos.user.model.dto;

import jakarta.validation.constraints.NotBlank;

public record PortalAcceptRequest(
        @NotBlank(message = "Токен приглашения обязателен")
        String token,
        @NotBlank(message = "Пароль обязателен")
        String password
) {}
