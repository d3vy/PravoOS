package com.pravoos.user.identity.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @NotBlank(message = "ФИО обязательно")
        @Size(max = 255, message = "ФИО не должно превышать 255 символов")
        String fullName,
    @Size(max = 255, message = "Специализация не должна превышать 255 символов")
        String specialization,
    @Size(max = 50, message = "Номер телефона не должен превышать 50 символов") String phone) {}
