package com.pravoos.ai.core.internal.dto;

import com.pravoos.ai.shared.model.enums.ContractRiskLevel;
import com.pravoos.ai.shared.model.enums.DiffChangeType;
import java.util.List;

public record DiffChange(
    int order,
    DiffChangeType type,
    String baseText,
    String revisedText,
    ContractRiskLevel riskLevel,
    String comment,
    List<DiffSegment> segments) {

  public DiffChange {
    segments = segments == null ? List.of() : List.copyOf(segments);
  }

  public DiffChange withAssessment(ContractRiskLevel level, String riskComment) {
    return new DiffChange(order, type, baseText, revisedText, level, riskComment, segments);
  }

  public DiffChange withSegments(List<DiffSegment> inlineSegments) {
    return new DiffChange(order, type, baseText, revisedText, riskLevel, comment, inlineSegments);
  }
}
