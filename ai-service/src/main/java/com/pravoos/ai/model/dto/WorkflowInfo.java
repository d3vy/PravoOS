package com.pravoos.ai.model.dto;

import com.pravoos.ai.model.enums.BankruptcyWorkflow;

public record WorkflowInfo(
        String id,
        String displayName,
        String instruction
) {
    public static WorkflowInfo from(BankruptcyWorkflow workflow) {
        return new WorkflowInfo(workflow.name(), workflow.displayName(), workflow.instruction());
    }
}
