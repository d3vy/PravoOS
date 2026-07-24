package com.pravoos.llm.openai.dto;

import com.pravoos.llm.domain.LlmUsage;
import java.util.List;

public record OpenAiStreamChunk(List<Choice> choices, OpenAiChatResponse.Usage usage) {
  public record Choice(Delta delta) {}

  public record Delta(String content) {}

  public String firstDelta() {
    if (choices == null || choices.isEmpty()) return null;
    Choice first = choices.get(0);
    if (first == null || first.delta() == null) return null;
    return first.delta().content();
  }

  public LlmUsage toLlmUsage() {
    if (usage == null) return LlmUsage.EMPTY;
    return new LlmUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens());
  }
}
