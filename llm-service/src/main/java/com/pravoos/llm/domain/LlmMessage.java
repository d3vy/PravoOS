package com.pravoos.llm.domain;

import java.util.List;

public record LlmMessage(
    String role, String content, List<LlmToolCall> toolCalls, String toolCallId) {

  public static final String ROLE_SYSTEM = "system";
  public static final String ROLE_USER = "user";
  public static final String ROLE_ASSISTANT = "assistant";
  public static final String ROLE_TOOL = "tool";

  public LlmMessage {
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
  }

  public LlmMessage(String role, String content) {
    this(role, content, List.of(), null);
  }

  public static LlmMessage system(String content) {
    return new LlmMessage(ROLE_SYSTEM, content);
  }

  public static LlmMessage user(String content) {
    return new LlmMessage(ROLE_USER, content);
  }

  public static LlmMessage assistant(String content, List<LlmToolCall> toolCalls) {
    return new LlmMessage(ROLE_ASSISTANT, content, toolCalls, null);
  }

  public static LlmMessage tool(String toolCallId, String content) {
    return new LlmMessage(ROLE_TOOL, content, List.of(), toolCallId);
  }

  public boolean hasToolCalls() {
    return !toolCalls.isEmpty();
  }
}
