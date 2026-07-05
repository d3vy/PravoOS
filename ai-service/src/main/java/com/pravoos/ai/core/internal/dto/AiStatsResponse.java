package com.pravoos.ai.core.internal.dto;

import java.util.List;

public record AiStatsResponse(
        long totalResponses,
        long ratedResponses,
        long positiveRatings,
        long negativeRatings,
        List<WorkflowStat> workflows
) {}
