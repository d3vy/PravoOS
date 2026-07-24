package com.pravoos.user.identity.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MfaLoginRequest(
    @NotBlank String mfaToken,
    @NotBlank @Pattern(regexp = "\\d{6}", message = "Код должен состоять из 6 цифр") String code) {}
