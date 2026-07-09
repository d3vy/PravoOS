package com.pravoos.ai.practice.internal.dto;

import java.util.List;

public record CaseAnalyticsResponse(
        CaseTimelineStats timeline,
        List<CourtStat> courtStats,
        AiCaseAnalysisDto aiAnalysis
) {}
