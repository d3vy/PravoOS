package com.pravoos.ai.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DeepSeekChatRequest(
        String model,
        List<LlmMessage> messages,
        @JsonProperty("max_tokens") int maxTokens,
        double temperature
) {}
