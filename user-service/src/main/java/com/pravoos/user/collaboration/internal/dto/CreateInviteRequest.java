package com.pravoos.user.collaboration.internal.dto;

import com.pravoos.user.collaboration.internal.model.enums.OrgRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInviteRequest(
    @NotBlank(message = "Email обязателен")
        @Email(message = "Некорректный email")
        @Size(max = 320, message = "Email слишком длинный")
        String email,
    @NotNull(message = "Роль обязательна") OrgRole orgRole) {}
