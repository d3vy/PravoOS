package com.pravoos.llm.domain;

public record LlmToolCall(String id, String name, String argumentsJson) {

  public LlmToolCall withArgumentsJson(String restoredArgumentsJson) {
    return new LlmToolCall(id, name, restoredArgumentsJson);
  }
}
