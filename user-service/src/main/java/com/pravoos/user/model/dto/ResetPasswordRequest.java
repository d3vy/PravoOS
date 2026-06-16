package com.pravoos.user.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Pattern(
                regexp = "(?=.*[A-Za-zА-Яа-яЁё])(?=.*\\d).{8,100}",
                message = "Пароль — не менее 8 символов, с буквой и цифрой"
        ) String password
) {}
