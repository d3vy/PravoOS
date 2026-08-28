package com.pravoos.ai.core.internal.dto;

import com.pravoos.ai.core.internal.model.entity.AiTrustedTool;
import java.time.LocalDateTime;

public record AiTrustedToolResponse(String toolName, LocalDateTime grantedAt) {

  public static AiTrustedToolResponse from(AiTrustedTool trustedTool) {
    return new AiTrustedToolResponse(trustedTool.getToolName(), trustedTool.getGrantedAt());
  }
}
