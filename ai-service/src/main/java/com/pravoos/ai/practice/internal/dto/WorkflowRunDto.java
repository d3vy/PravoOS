package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.WorkflowStepRun;
import com.pravoos.ai.practice.internal.model.entity.WorkflowRun;
import com.pravoos.ai.shared.model.enums.WorkflowCategory;
import com.pravoos.ai.shared.model.enums.WorkflowRunStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record WorkflowRunDto(
        UUID id,
        UUID caseId,
        UUID definitionId,
        String definitionName,
        WorkflowCategory category,
        String categoryName,
        WorkflowRunStatus status,
        String statusName,
        List<WorkflowStepRun> steps,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
    public static WorkflowRunDto from(WorkflowRun run) {
        return new WorkflowRunDto(
                run.getId(),
                run.getCaseId(),
                run.getDefinitionId(),
                run.getDefinitionName(),
                run.getCategory(),
                run.getCategory().getDisplayName(),
                run.getStatus(),
                run.getStatus().getDisplayName(),
                run.getSteps() != null ? run.getSteps() : List.of(),
                run.getStartedAt(),
                run.getFinishedAt());
    }
}
