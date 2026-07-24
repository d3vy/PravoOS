package com.pravoos.ai.core.internal.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateTabularReviewRequest(
    @NotNull UUID caseId,
    @Size(max = 300) String title,
    @NotEmpty List<@NotNull UUID> documentIds,
    @NotEmpty List<@NotNull @Size(min = 3, max = 300) String> questions) {}
