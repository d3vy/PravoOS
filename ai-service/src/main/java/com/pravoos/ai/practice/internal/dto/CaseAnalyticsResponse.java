package com.pravoos.ai.practice.internal.dto;

import java.util.List;

public record CaseAnalyticsResponse(
        CaseTimelineStats timeline,
        List<OutcomeStat> courtStats,
        List<OutcomeStat> judgeStats,
        List<OutcomeStat> partyStats,
        AiCaseAnalysisDto aiAnalysis
) {}
