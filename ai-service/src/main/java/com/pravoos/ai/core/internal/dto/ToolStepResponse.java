package com.pravoos.ai.core.internal.dto;

import com.pravoos.ai.core.internal.model.mongo.ToolStepDoc;

public record ToolStepResponse(String name, String status, long durationMs) {

  public static ToolStepResponse from(ToolStepDoc toolStep) {
    return new ToolStepResponse(toolStep.getName(), toolStep.getStatus(), toolStep.getDurationMs());
  }
}
