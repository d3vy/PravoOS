package com.pravoos.ai.core.internal.dto;

public record WorkflowStat(
        String workflowId,
        String workflowName,
        long count,
        Double avgRating
) {}
