package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.shared.model.enums.ClientType;
import jakarta.validation.constraints.*;

public record CreateClientRequest(
        @NotBlank @Size(max = 300) String name,
        @NotNull ClientType type,
        @Pattern(regexp = "(\\+?[\\d\\s()\\-]{10,20})?", message = "Некорректный номер телефона") String phone,
        @Email @Size(max = 255) String email,
        @Pattern(regexp = "(\\d{10}|\\d{12})?", message = "ИНН должен содержать 10 или 12 цифр") String inn,
        @Size(max = 5000) String notes
) {}
