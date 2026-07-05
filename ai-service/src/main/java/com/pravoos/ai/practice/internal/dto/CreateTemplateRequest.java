package com.pravoos.ai.practice.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTemplateRequest(
        @NotBlank @Size(max = 300) String name,
        @NotBlank @Size(max = 50_000) String content
) {}
