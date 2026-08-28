package com.pravoos.ai.core.internal.agent;

import com.pravoos.ai.llm.api.LlmUsage;
import java.util.List;

public record AgentResult(String content, LlmUsage usage, List<ToolStep> steps, int iterations) {

  public AgentResult {
    content = content == null ? "" : content;
    usage = usage == null ? LlmUsage.EMPTY : usage;
    steps = steps == null ? List.of() : List.copyOf(steps);
  }
}
