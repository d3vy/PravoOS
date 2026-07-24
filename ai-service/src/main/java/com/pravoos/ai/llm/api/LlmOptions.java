package com.pravoos.ai.llm.api;

public record LlmOptions(String modelProfile, Integer maxTokens, Double temperature) {

  public static final String GUARD_PROFILE = "guard";
  public static final String RERANK_PROFILE = "rerank";
  public static final LlmOptions DEFAULT = new LlmOptions(null, null, null);

  public static LlmOptions guard(int maxTokens, double temperature) {
    return new LlmOptions(GUARD_PROFILE, maxTokens, temperature);
  }

  public static LlmOptions rerank(int maxTokens, double temperature) {
    return new LlmOptions(RERANK_PROFILE, maxTokens, temperature);
  }
}
