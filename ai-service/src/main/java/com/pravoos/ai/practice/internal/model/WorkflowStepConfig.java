package com.pravoos.ai.practice.internal.model;

import com.pravoos.ai.shared.model.enums.DeadlineType;
import com.pravoos.ai.shared.model.enums.WorkflowStepType;

public record WorkflowStepConfig(
        int order,
        WorkflowStepType type,
        String title,
        String instruction,
        String draftType,
        DeadlineType deadlineType,
        Integer deadlineOffsetDays
) {}
