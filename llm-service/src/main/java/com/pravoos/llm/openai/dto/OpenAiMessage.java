package com.pravoos.llm.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pravoos.llm.domain.LlmMessage;
import com.pravoos.llm.domain.LlmToolCall;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiMessage(
    String role,
    String content,
    @JsonProperty("tool_calls") List<ToolCall> toolCalls,
    @JsonProperty("tool_call_id") String toolCallId) {

  private static final String FUNCTION_TYPE = "function";

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ToolCall(String id, String type, Function function) {

    public record Function(String name, String arguments) {}
  }

  public static OpenAiMessage from(LlmMessage message) {
    return new OpenAiMessage(
        message.role(),
        message.content(),
        toWireToolCalls(message.toolCalls()),
        message.toolCallId());
  }

  public List<LlmToolCall> toDomainToolCalls() {
    if (toolCalls == null || toolCalls.isEmpty()) {
      return List.of();
    }
    return toolCalls.stream()
        .filter(call -> call != null && call.function() != null)
        .map(
            call ->
                new LlmToolCall(
                    call.id(),
                    call.function().name(),
                    call.function().arguments() == null ? "{}" : call.function().arguments()))
        .toList();
  }

  private static List<ToolCall> toWireToolCalls(List<LlmToolCall> toolCalls) {
    if (toolCalls == null || toolCalls.isEmpty()) {
      return null;
    }
    return toolCalls.stream()
        .map(
            call ->
                new ToolCall(
                    call.id(),
                    FUNCTION_TYPE,
                    new ToolCall.Function(call.name(), call.argumentsJson())))
        .toList();
  }
}
