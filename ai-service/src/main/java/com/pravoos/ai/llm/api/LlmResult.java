package com.pravoos.ai.llm.api;

import java.util.List;

public record LlmResult(
    String content, LlmUsage usage, List<LlmToolCall> toolCalls, String finishReason) {

  public LlmResult {
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
  }

  public LlmResult(String content, LlmUsage usage) {
    this(content, usage, List.of(), null);
  }

  public boolean hasToolCalls() {
    return !toolCalls.isEmpty();
  }
}
