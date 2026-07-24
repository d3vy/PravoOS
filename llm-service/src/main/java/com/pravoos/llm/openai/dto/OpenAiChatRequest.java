package com.pravoos.llm.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pravoos.llm.domain.LlmMessage;
import java.util.List;

public record OpenAiChatRequest(
    String model,
    List<LlmMessage> messages,
    @JsonProperty("max_tokens") int maxTokens,
    double temperature) {}
