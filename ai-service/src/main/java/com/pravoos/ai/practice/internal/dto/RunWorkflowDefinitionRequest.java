package com.pravoos.ai.practice.internal.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RunWorkflowDefinitionRequest(
        @NotNull UUID definitionId
) {}
