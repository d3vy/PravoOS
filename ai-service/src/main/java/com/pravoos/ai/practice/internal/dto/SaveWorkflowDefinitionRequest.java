package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.shared.model.enums.WorkflowCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SaveWorkflowDefinitionRequest(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 1000) String description,
    @NotNull WorkflowCategory category,
    @NotEmpty @Size(max = 20) @Valid List<WorkflowStepDto> steps) {}
