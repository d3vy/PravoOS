package com.pravoos.ai.core.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CheckCitationsRequest(@NotBlank @Size(max = 50000) String text) {}
