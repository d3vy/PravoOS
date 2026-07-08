package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.WorkflowStepConfig;
import com.pravoos.ai.practice.internal.model.entity.WorkflowDefinition;
import com.pravoos.ai.shared.model.enums.WorkflowCategory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record WorkflowDefinitionDto(
        UUID id,
        String name,
        String description,
        WorkflowCategory category,
        String categoryName,
        boolean system,
        boolean editable,
        List<WorkflowStepConfig> steps,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static WorkflowDefinitionDto from(WorkflowDefinition definition, boolean editable) {
        return new WorkflowDefinitionDto(
                definition.getId(),
                definition.getName(),
                definition.getDescription(),
                definition.getCategory(),
                definition.getCategory().getDisplayName(),
                definition.isSystem(),
                editable,
                definition.getSteps() != null ? definition.getSteps() : List.of(),
                definition.getCreatedAt(),
                definition.getUpdatedAt());
    }
}
