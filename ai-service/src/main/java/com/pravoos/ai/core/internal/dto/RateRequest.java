package com.pravoos.ai.core.internal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RateRequest(
        @NotNull @Min(-1) @Max(1) Integer rating,
        @Size(max = 2000) String comment
) {}
