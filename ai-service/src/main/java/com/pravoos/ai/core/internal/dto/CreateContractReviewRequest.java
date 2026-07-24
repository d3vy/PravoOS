package com.pravoos.ai.core.internal.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateContractReviewRequest(@NotNull UUID documentId) {}
