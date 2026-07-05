package com.pravoos.ai.llm.internal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pravoos.ai.llm.api.LlmMessage;

import java.util.List;

public record OpenAiChatRequest(
        String model,
        List<LlmMessage> messages,
        @JsonProperty("max_tokens") int maxTokens,
        double temperature
) {}
