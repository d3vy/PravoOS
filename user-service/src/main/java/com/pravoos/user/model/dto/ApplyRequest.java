package com.pravoos.user.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplyRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 2, max = 255) String fullName,
        @NotBlank @Size(min = 8, max = 100) String password,
        @Size(max = 100) String barNumber,
        @Size(max = 255) String specialization,
        @Size(max = 50) String phone
) {}
