package com.pravoos.ai.model.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateContractReviewRequest(
        @NotNull UUID documentId
) {}
