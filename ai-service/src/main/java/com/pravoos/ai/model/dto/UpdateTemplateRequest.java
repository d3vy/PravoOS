package com.pravoos.ai.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTemplateRequest(
        @NotBlank @Size(max = 300) String name,
        @NotBlank String content
) {}
