package com.pravoos.llm.openai;

import com.pravoos.llm.domain.LlmToolCall;
import com.pravoos.llm.domain.LlmUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LlmMetrics {

  private final Counter completionRequests;
  private final Counter promptTokens;
  private final Counter completionTokens;
  private final MeterRegistry registry;

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
}
