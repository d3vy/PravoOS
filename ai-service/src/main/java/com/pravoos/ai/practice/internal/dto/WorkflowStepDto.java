package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.WorkflowStepConfig;
import com.pravoos.ai.shared.model.enums.DeadlineType;
import com.pravoos.ai.shared.model.enums.WorkflowStepType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkflowStepDto(
    @NotNull WorkflowStepType type,
    @NotBlank @Size(max = 200) String title,
    @Size(max = 4000) String instruction,
    @Size(max = 40) String draftType,
    DeadlineType deadlineType,
    @Min(0) @Max(3650) Integer deadlineOffsetDays) {
  public WorkflowStepConfig toConfig(int order) {
    return new WorkflowStepConfig(
        order,
        type,
        title != null ? title.trim() : null,
        instruction != null ? instruction.trim() : null,
        draftType != null ? draftType.trim() : null,
        deadlineType,
        deadlineOffsetDays);
  }
}
