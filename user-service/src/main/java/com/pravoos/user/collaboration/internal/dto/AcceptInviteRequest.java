package com.pravoos.user.collaboration.internal.dto;

import jakarta.validation.constraints.NotBlank;

public record AcceptInviteRequest(
        @NotBlank(message = "Токен приглашения обязателен")
        String token
) {
}
