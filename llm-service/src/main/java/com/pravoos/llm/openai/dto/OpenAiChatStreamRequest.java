package com.pravoos.llm.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pravoos.llm.domain.LlmMessage;

import java.util.List;

public record OpenAiChatStreamRequest(
        String model,
        List<LlmMessage> messages,
        @JsonProperty("max_tokens") int maxTokens,
        double temperature,
        boolean stream,
        @JsonProperty("stream_options") StreamOptions streamOptions
) {
    public record StreamOptions(@JsonProperty("include_usage") boolean includeUsage) {}

    public static OpenAiChatStreamRequest withUsage(String model, List<LlmMessage> messages,
                                                    int maxTokens, double temperature) {
        return new OpenAiChatStreamRequest(model, messages, maxTokens, temperature, true, new StreamOptions(true));
    }
}
