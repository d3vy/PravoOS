package com.pravoos.ai.llm.api;

import java.util.List;

public record LlmStreamResult(LlmUsage usage, List<LlmToolCall> toolCalls, String finishReason) {

  public static final LlmStreamResult EMPTY = new LlmStreamResult(LlmUsage.EMPTY, List.of(), null);

  public LlmStreamResult {
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
  }

  public boolean hasToolCalls() {
    return !toolCalls.isEmpty();
  }
}
