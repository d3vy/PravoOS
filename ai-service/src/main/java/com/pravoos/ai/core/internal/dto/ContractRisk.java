package com.pravoos.ai.core.internal.dto;

import com.pravoos.ai.shared.model.enums.ContractRiskLevel;

public record ContractRisk(
    String clause,
    String category,
    ContractRiskLevel level,
    String explanation,
    String recommendation) {}
