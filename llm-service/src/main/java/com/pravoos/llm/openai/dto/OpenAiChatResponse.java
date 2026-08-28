package com.pravoos.llm.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pravoos.llm.domain.LlmToolCall;
import com.pravoos.llm.domain.LlmUsage;
import java.util.List;

public record OpenAiChatResponse(List<Choice> choices, Usage usage) {
  public record Choice(OpenAiMessage message, @JsonProperty("finish_reason") String finishReason) {}

  public record Usage(
      @JsonProperty("prompt_tokens") int promptTokens,
      @JsonProperty("completion_tokens") int completionTokens,
      @JsonProperty("total_tokens") int totalTokens) {}

  public String firstContent() {
    Choice first = firstChoice();
    if (first == null || first.message() == null || first.message().content() == null) return "";
    return first.message().content();
  }

  public List<LlmToolCall> firstToolCalls() {
    Choice first = firstChoice();
    if (first == null || first.message() == null) return List.of();
    return first.message().toDomainToolCalls();
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
