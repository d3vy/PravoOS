package com.pravoos.llm.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiChatRequest(
    String model,
    List<OpenAiMessage> messages,
    @JsonProperty("max_completion_tokens") int maxTokens,
    List<OpenAiTool> tools,
    @JsonProperty("tool_choice") String toolChoice) {}
