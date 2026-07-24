package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.shared.model.enums.BankruptcyWorkflow;

public record WorkflowInfo(String id, String displayName, String instruction) {
  public static WorkflowInfo from(BankruptcyWorkflow workflow) {
    return new WorkflowInfo(workflow.name(), workflow.displayName(), workflow.instruction());
  }
}
