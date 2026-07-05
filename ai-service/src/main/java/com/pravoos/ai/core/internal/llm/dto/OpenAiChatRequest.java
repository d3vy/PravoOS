package com.pravoos.ai.core.internal.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OpenAiChatRequest(
        String model,
        List<LlmMessage> messages,
        @JsonProperty("max_tokens") int maxTokens,
        double temperature
) {}
