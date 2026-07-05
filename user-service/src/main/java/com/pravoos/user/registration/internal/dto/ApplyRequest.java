package com.pravoos.user.registration.internal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ApplyRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 2, max = 255) String fullName,
        @NotBlank @Pattern(
                regexp = "(?=.*[A-Za-zА-Яа-яЁё])(?=.*\\d).{8,100}",
                message = "Пароль — не менее 8 символов, с буквой и цифрой"
        ) String password,
        @NotBlank @Size(max = 255) String specialization,
        @NotBlank @Pattern(regexp = "\\+?[\\d\\s()\\-]{10,20}", message = "Некорректный телефон") String phone
) {}
