package com.pravoos.ai.model.dto;

public record WorkflowStat(
        String workflowId,
        String workflowName,
        long count,
        Double avgRating
) {}
