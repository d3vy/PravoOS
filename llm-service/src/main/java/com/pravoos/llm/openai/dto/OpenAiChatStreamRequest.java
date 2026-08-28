package com.pravoos.llm.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiChatStreamRequest(
    String model,
    List<OpenAiMessage> messages,
    @JsonProperty("max_tokens") int maxTokens,
    double temperature,
    boolean stream,
    @JsonProperty("stream_options") StreamOptions streamOptions,
    List<OpenAiTool> tools,
    @JsonProperty("tool_choice") String toolChoice) {

  public record StreamOptions(@JsonProperty("include_usage") boolean includeUsage) {}

  public static OpenAiChatStreamRequest withUsage(
      String model,
      List<OpenAiMessage> messages,
      int maxTokens,
      double temperature,
      List<OpenAiTool> tools,
      String toolChoice) {
    return new OpenAiChatStreamRequest(
        model, messages, maxTokens, temperature, true, new StreamOptions(true), tools, toolChoice);
  }
}
