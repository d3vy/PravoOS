package com.pravoos.ai.model.dto;

import com.pravoos.ai.model.enums.ClientType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateClientRequest(
        @NotBlank @Size(max = 300) String name,
        @NotNull ClientType type,
        @Pattern(regexp = "(\\+?[\\d\\s()\\-]{10,20})?", message = "Некорректный номер телефона") String phone,
        @Email @Size(max = 255) String email,
        @Pattern(regexp = "(\\d{10}|\\d{12})?", message = "ИНН должен содержать 10 или 12 цифр") String inn,
        @Size(max = 5000) String notes
) {}
