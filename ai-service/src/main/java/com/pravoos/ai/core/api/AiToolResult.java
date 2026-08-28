package com.pravoos.ai.core.api;

public record AiToolResult(boolean ok, String content) {

  public AiToolResult {
    content = content == null ? "" : content;
  }

  public static AiToolResult ok(String content) {
    return new AiToolResult(true, content);
  }

  public static AiToolResult error(String message) {
    return new AiToolResult(false, message);
  }
}
