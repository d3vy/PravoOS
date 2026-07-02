package com.pravoos.ai.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CheckCitationsRequest(
        @NotBlank @Size(max = 50000) String text
) {}
