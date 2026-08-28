package com.pravoos.ai.core.internal.model.mongo;

public class ToolStepDoc {

  private String name;

  private String status;

  private String resultPreview;

  private long durationMs;

  public ToolStepDoc() {}

  public ToolStepDoc(String name, String status, String resultPreview, long durationMs) {
    this.name = name;
    this.status = status;
    this.resultPreview = resultPreview;
    this.durationMs = durationMs;
  }

  public String getName() {
    return name;
  }

  public String getStatus() {
    return status;
  }

  public String getResultPreview() {
    return resultPreview;
  }

  public long getDurationMs() {
    return durationMs;
  }
}
