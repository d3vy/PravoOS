package com.pravoos.llm.domain;

import java.util.List;

public record LlmStreamResult(LlmUsage usage, List<LlmToolCall> toolCalls, String finishReason) {

  public static final LlmStreamResult EMPTY = new LlmStreamResult(LlmUsage.EMPTY, List.of(), null);

  public LlmStreamResult {
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
  }

  public static LlmStreamResult usageOnly(LlmUsage usage) {
    return new LlmStreamResult(usage == null ? LlmUsage.EMPTY : usage, List.of(), null);
  }

  public boolean hasToolCalls() {
    return !toolCalls.isEmpty();
  }
}
