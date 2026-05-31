package com.pravoos.ai.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCaseRequest(
        @NotBlank @Size(max = 500) String title,
        @Size(max = 5000) String description
) {}
