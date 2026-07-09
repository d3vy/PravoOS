package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.entity.CaseAnalysis;

import java.time.LocalDateTime;

public record AiCaseAnalysisDto(String content, LocalDateTime generatedAt) {

    public static AiCaseAnalysisDto from(CaseAnalysis analysis) {
        return new AiCaseAnalysisDto(analysis.getContent(), analysis.getGeneratedAt());
    }
}
