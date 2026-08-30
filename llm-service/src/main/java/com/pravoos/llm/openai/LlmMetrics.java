package com.pravoos.llm.openai;

import com.pravoos.llm.domain.LlmToolCall;
import com.pravoos.llm.domain.LlmUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class LlmMetrics {

  private final Counter completionRequests;
  private final Counter promptTokens;
  private final Counter completionTokens;
  private final MeterRegistry registry;
  private final AtomicInteger activeStreams = new AtomicInteger();

  public LlmMetrics(MeterRegistry registry) {
    this.registry = registry;
    this.completionRequests =
        Counter.builder("pravoos.llm.requests")
            .description("Number of successful LLM chat-completion calls")
            .register(registry);
    this.promptTokens =
        Counter.builder("pravoos.llm.tokens")
            .description("Tokens consumed by LLM chat-completion calls")
            .tag("type", "prompt")
            .register(registry);
    this.completionTokens =
        Counter.builder("pravoos.llm.tokens")
            .description("Tokens consumed by LLM chat-completion calls")
            .tag("type", "completion")
            .register(registry);
    Gauge.builder("pravoos.llm.streams.active", activeStreams, AtomicInteger::get)
        .description("Number of SSE completion streams currently open")
        .register(registry);
  }

  public void recordCompletion(LlmUsage usage) {
    completionRequests.increment();
    promptTokens.increment(usage.promptTokens());
    completionTokens.increment(usage.completionTokens());
  }

  public void recordToolCalls(List<LlmToolCall> toolCalls) {
    if (toolCalls == null || toolCalls.isEmpty()) {
      return;
    }
    for (LlmToolCall toolCall : toolCalls) {
      Counter.builder("pravoos.llm.tool_calls")
          .description("Tool calls requested by the model")
          .tag("name", toolCall.name() == null ? "unknown" : toolCall.name())
          .register(registry)
          .increment();
    }
  }

  public void streamStarted() {
    activeStreams.incrementAndGet();
  }

  public void streamEnded() {
    activeStreams.decrementAndGet();
  }
}
