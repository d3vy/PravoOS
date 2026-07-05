package com.pravoos.user.collaboration.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotBlank(message = "Название организации обязательно")
        @Size(max = 200, message = "Название не должно превышать 200 символов")
        String name
) {
}
