package com.pravoos.user.identity.internal.dto;

import jakarta.validation.constraints.Pattern;

public record UpdateLanguageRequest(
        @Pattern(regexp = "ru|en", message = "Неподдерживаемый язык")
        String language
) {}
