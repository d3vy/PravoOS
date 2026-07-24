package com.pravoos.llm.domain;

public record LlmOptions(String modelProfile, Integer maxTokens, Double temperature) {

  public static final String GUARD_PROFILE = "guard";
  public static final String RERANK_PROFILE = "rerank";
  public static final LlmOptions DEFAULT = new LlmOptions(null, null, null);

  public static LlmOptions orDefault(LlmOptions options) {
    return options == null ? DEFAULT : options;
  }

  public boolean isGuardProfile() {
    return GUARD_PROFILE.equalsIgnoreCase(modelProfile);
  }

  public boolean isRerankProfile() {
    return RERANK_PROFILE.equalsIgnoreCase(modelProfile);
  }
}
