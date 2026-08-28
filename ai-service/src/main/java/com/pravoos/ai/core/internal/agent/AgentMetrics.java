package com.pravoos.ai.core.internal.agent;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class AgentMetrics {

  private final MeterRegistry registry;
  private final Counter turns;
  private final DistributionSummary iterations;
  private final DistributionSummary tokens;
  private final Timer turnDuration;

  public AgentMetrics(MeterRegistry registry) {
    this.registry = registry;
    this.turns =
        Counter.builder("pravoos.agent.turns")
            .description("Number of agent chat turns")
            .register(registry);
    this.iterations =
        DistributionSummary.builder("pravoos.agent.iterations")
            .description("Model-call iterations per agent turn")
            .register(registry);
    this.tokens =
        DistributionSummary.builder("pravoos.agent.tokens")
            .description("Total tokens consumed per agent turn")
            .register(registry);
    this.turnDuration =
        Timer.builder("pravoos.agent.turn_duration")
            .description("Wall-clock duration of an agent turn")
            .register(registry);
  }

  public void recordTurn(int iterationCount, int totalTokens, Duration duration) {
    turns.increment();
    iterations.record(iterationCount);
    tokens.record(totalTokens);
    turnDuration.record(duration);
  }

  public void recordToolCall(String toolName, ToolStepStatus status) {
    Counter.builder("pravoos.agent.tool_calls")
        .description("Agent tool call outcomes")
        .tag("name", toolName == null ? "unknown" : toolName)
        .tag("status", status.name().toLowerCase())
        .register(registry)
        .increment();
  }

  public void recordProposal(String toolName, String status) {
    recordProposal(toolName, status, 1);
  }

  public void recordProposal(String toolName, String status, int count) {
    Counter.builder("pravoos.agent.proposals")
        .description("Agent action proposal transitions")
        .tag("tool", toolName == null ? "unknown" : toolName)
        .tag("status", status)
        .register(registry)
        .increment(count);
  }
}
