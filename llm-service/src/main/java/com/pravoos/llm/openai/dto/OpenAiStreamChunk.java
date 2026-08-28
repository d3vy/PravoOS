package com.pravoos.llm.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pravoos.llm.domain.LlmUsage;
import java.util.List;

public record OpenAiStreamChunk(List<Choice> choices, OpenAiChatResponse.Usage usage) {

  public record Choice(Delta delta, @JsonProperty("finish_reason") String finishReason) {}

  public record Delta(String content, @JsonProperty("tool_calls") List<ToolCallDelta> toolCalls) {}

  public record ToolCallDelta(Integer index, String id, String type, FunctionDelta function) {}

  public record FunctionDelta(String name, String arguments) {}

  public String firstDelta() {
    Choice first = firstChoice();
    if (first == null || first.delta() == null) return null;
    return first.delta().content();
  }

  public List<ToolCallDelta> firstToolCallDeltas() {
    Choice first = firstChoice();
    if (first == null || first.delta() == null || first.delta().toolCalls() == null) {
      return List.of();
    }
    return first.delta().toolCalls();
  }

  public String firstFinishReason() {
    Choice first = firstChoice();
    return first == null ? null : first.finishReason();
  }

  public LlmUsage toLlmUsage() {
    if (usage == null) return LlmUsage.EMPTY;
    return new LlmUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens());
  }

  private Choice firstChoice() {
    if (choices == null || choices.isEmpty()) return null;
    return choices.get(0);
  }
}
