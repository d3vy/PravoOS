package com.pravoos.ai.core.internal.dto;

import com.pravoos.ai.shared.model.enums.ContractRiskLevel;
import com.pravoos.ai.shared.model.enums.DiffChangeType;

public record DiffChange(
        int order,
        DiffChangeType type,
        String baseText,
        String revisedText,
        ContractRiskLevel riskLevel,
        String comment
) {
    public DiffChange withAssessment(ContractRiskLevel level, String riskComment) {
        return new DiffChange(order, type, baseText, revisedText, level, riskComment);
    }
}
