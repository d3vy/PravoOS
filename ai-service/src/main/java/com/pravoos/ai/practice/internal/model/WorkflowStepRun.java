package com.pravoos.ai.practice.internal.model;

import com.pravoos.ai.shared.model.enums.WorkflowStepStatus;
import com.pravoos.ai.shared.model.enums.WorkflowStepType;
import java.util.UUID;

public record WorkflowStepRun(
    int order,
    WorkflowStepType type,
    String title,
    WorkflowStepStatus status,
    String detail,
    UUID aiResponseId,
    UUID draftId,
    String error) {
  public static WorkflowStepRun pending(WorkflowStepConfig config) {
    return new WorkflowStepRun(
        config.order(),
        config.type(),
        config.title(),
        WorkflowStepStatus.PENDING,
        null,
        null,
        null,
        null);
  }

  public WorkflowStepRun completed(String detail, UUID aiResponseId, UUID draftId) {
    return new WorkflowStepRun(
        order, type, title, WorkflowStepStatus.COMPLETED, detail, aiResponseId, draftId, null);
  }

  public WorkflowStepRun skipped(String detail) {
    return new WorkflowStepRun(
        order, type, title, WorkflowStepStatus.SKIPPED, detail, null, null, null);
  }

  public WorkflowStepRun failed(String error) {
    return new WorkflowStepRun(
        order, type, title, WorkflowStepStatus.FAILED, null, null, null, error);
  }
}
