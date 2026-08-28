package com.pravoos.ai.llm.api;

import java.util.List;
import java.util.Set;

public record LlmOptions(
    String modelProfile,
    Integer maxTokens,
    Double temperature,
    List<ToolSpec> tools,
    String toolChoice) {

  public static final String GUARD_PROFILE = "guard";
  public static final String RERANK_PROFILE = "rerank";
  public static final String TOOL_CHOICE_AUTO = "auto";
  public static final String TOOL_CHOICE_NONE = "none";
  public static final String TOOL_CHOICE_REQUIRED = "required";

  private static final Set<String> ALLOWED_TOOL_CHOICES =
      Set.of(TOOL_CHOICE_AUTO, TOOL_CHOICE_NONE, TOOL_CHOICE_REQUIRED);

  public static final LlmOptions DEFAULT = new LlmOptions(null, null, null);

  public LlmOptions {
    tools = tools == null ? List.of() : List.copyOf(tools);
    if (toolChoice != null && !ALLOWED_TOOL_CHOICES.contains(toolChoice)) {
      throw new IllegalArgumentException(
          "Unsupported tool_choice '" + toolChoice + "', expected one of " + ALLOWED_TOOL_CHOICES);
    }
  }

  public LlmOptions(String modelProfile, Integer maxTokens, Double temperature) {
    this(modelProfile, maxTokens, temperature, List.of(), null);
  }

  public static LlmOptions guard(int maxTokens, double temperature) {
    return new LlmOptions(GUARD_PROFILE, maxTokens, temperature);
  }

  public static LlmOptions rerank(int maxTokens, double temperature) {
    return new LlmOptions(RERANK_PROFILE, maxTokens, temperature);
  }

  public static LlmOptions withTools(List<ToolSpec> tools, String toolChoice) {
    return new LlmOptions(null, null, null, tools, toolChoice);
  }

  public LlmOptions andTools(List<ToolSpec> toolSpecs, String choice) {
    return new LlmOptions(modelProfile, maxTokens, temperature, toolSpecs, choice);
  }

  public boolean hasTools() {
    return !tools.isEmpty();
  }
}
