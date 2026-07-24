package com.pravoos.ai.core.internal.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateComparisonRequest(
    @NotNull UUID baseDocumentId, @NotNull UUID revisedDocumentId) {}
