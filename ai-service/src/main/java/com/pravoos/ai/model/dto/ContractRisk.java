package com.pravoos.ai.model.dto;

import com.pravoos.ai.model.enums.ContractRiskLevel;

public record ContractRisk(
        String clause,
        String category,
        ContractRiskLevel level,
        String explanation,
        String recommendation
) {}
