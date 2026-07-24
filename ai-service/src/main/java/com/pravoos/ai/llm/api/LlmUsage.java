package com.pravoos.ai.llm.api;

public record LlmUsage(int promptTokens, int completionTokens, int totalTokens) {

  public static final LlmUsage EMPTY = new LlmUsage(0, 0, 0);
}
