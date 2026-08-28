package com.pravoos.llm.domain;

import com.pravoos.llm.exception.LlmException;
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
      throw new LlmException(
          "Unsupported tool_choice '" + toolChoice + "', expected one of " + ALLOWED_TOOL_CHOICES);
    }
  }

  public LlmOptions(String modelProfile, Integer maxTokens, Double temperature) {
    this(modelProfile, maxTokens, temperature, List.of(), null);
  }

  public static LlmOptions orDefault(LlmOptions options) {
    return options == null ? DEFAULT : options;
  }

  public LlmOptions withTools(List<ToolSpec> toolSpecs, String choice) {
    return new LlmOptions(modelProfile, maxTokens, temperature, toolSpecs, choice);
  }

  public boolean isGuardProfile() {
    return GUARD_PROFILE.equalsIgnoreCase(modelProfile);
  }

  public boolean isRerankProfile() {
    return RERANK_PROFILE.equalsIgnoreCase(modelProfile);
  }

  public boolean hasTools() {
    return !tools.isEmpty();
  }
}
