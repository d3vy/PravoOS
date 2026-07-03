package com.pravoos.user.model.dto;

import jakarta.validation.constraints.NotBlank;

public record AcceptInviteRequest(
        @NotBlank(message = "Токен приглашения обязателен")
        String token
) {
}
