package com.pravoos.ai.core.internal.agent;

public record ToolStep(
    String name,
    String argumentsJson,
    ToolStepStatus status,
    String resultPreview,
    long durationMs) {

  private static final int PREVIEW_MAX_CHARS = 300;

  public static ToolStep running(String name, String argumentsJson) {
    return new ToolStep(name, argumentsJson, ToolStepStatus.RUNNING, null, 0L);
  }

  public ToolStep finished(ToolStepStatus finalStatus, String content, long elapsedMs) {
    return new ToolStep(name, argumentsJson, finalStatus, preview(content), elapsedMs);
  }

  private static String preview(String content) {
    if (content == null) {
      return null;
    }
    return content.length() <= PREVIEW_MAX_CHARS
        ? content
        : content.substring(0, PREVIEW_MAX_CHARS) + "…";
  }
}
