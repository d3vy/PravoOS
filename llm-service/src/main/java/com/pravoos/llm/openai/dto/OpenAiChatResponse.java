package com.pravoos.llm.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pravoos.llm.domain.LlmMessage;
import com.pravoos.llm.domain.LlmUsage;

import java.util.List;

public record OpenAiChatResponse(
        List<Choice> choices,
        Usage usage
) {
    public record Choice(LlmMessage message) {}

    public record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens
    ) {}

    public String firstContent() {
        if (choices == null || choices.isEmpty()) return "";
        Choice first = choices.get(0);
        if (first == null || first.message() == null || first.message().content() == null) return "";
        return first.message().content();
    }

    public LlmUsage toLlmUsage() {
        if (usage == null) return LlmUsage.EMPTY;
        return new LlmUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens());
    }
}
