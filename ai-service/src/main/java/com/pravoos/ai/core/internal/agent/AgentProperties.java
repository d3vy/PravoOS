package com.pravoos.ai.core.internal.agent;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
    int maxIterations,
    int maxToolCalls,
    int maxResultChars,
    int maxArgumentsChars,
    Duration maxDuration,
    long maxTotalTokens,
    Duration proposalTtl,
    int maxWriteActionsPerHour) {

  private static final int DEFAULT_MAX_ITERATIONS = 8;
  private static final int DEFAULT_MAX_TOOL_CALLS = 12;
  private static final int DEFAULT_MAX_RESULT_CHARS = 8000;
  private static final int DEFAULT_MAX_ARGUMENTS_CHARS = 8000;
  private static final Duration DEFAULT_MAX_DURATION = Duration.ofSeconds(120);
  private static final long DEFAULT_MAX_TOTAL_TOKENS = 120_000L;
  private static final Duration DEFAULT_PROPOSAL_TTL = Duration.ofMinutes(30);
  private static final int DEFAULT_MAX_WRITE_ACTIONS_PER_HOUR = 30;

  public AgentProperties {
    maxIterations = maxIterations <= 0 ? DEFAULT_MAX_ITERATIONS : maxIterations;
    maxToolCalls = maxToolCalls <= 0 ? DEFAULT_MAX_TOOL_CALLS : maxToolCalls;
    maxResultChars = maxResultChars <= 0 ? DEFAULT_MAX_RESULT_CHARS : maxResultChars;
    maxArgumentsChars = maxArgumentsChars <= 0 ? DEFAULT_MAX_ARGUMENTS_CHARS : maxArgumentsChars;
    maxDuration =
        maxDuration == null || maxDuration.isZero() || maxDuration.isNegative()
            ? DEFAULT_MAX_DURATION
            : maxDuration;
    maxTotalTokens = maxTotalTokens <= 0 ? DEFAULT_MAX_TOTAL_TOKENS : maxTotalTokens;
    proposalTtl =
        proposalTtl == null || proposalTtl.isZero() || proposalTtl.isNegative()
            ? DEFAULT_PROPOSAL_TTL
            : proposalTtl;
    maxWriteActionsPerHour =
        maxWriteActionsPerHour <= 0 ? DEFAULT_MAX_WRITE_ACTIONS_PER_HOUR : maxWriteActionsPerHour;
  }
}
