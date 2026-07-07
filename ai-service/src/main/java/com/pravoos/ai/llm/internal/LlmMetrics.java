package com.pravoos.ai.llm.internal;

import com.pravoos.ai.llm.api.LlmUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class LlmMetrics {

    private final Counter completionRequests;
    private final Counter promptTokens;
    private final Counter completionTokens;

    public LlmMetrics(MeterRegistry registry) {
        this.completionRequests = Counter.builder("pravoos.llm.requests")
                .description("Number of successful LLM chat-completion calls")
                .register(registry);
        this.promptTokens = Counter.builder("pravoos.llm.tokens")
                .description("Tokens consumed by LLM chat-completion calls")
                .tag("type", "prompt")
                .register(registry);
        this.completionTokens = Counter.builder("pravoos.llm.tokens")
                .description("Tokens consumed by LLM chat-completion calls")
                .tag("type", "completion")
                .register(registry);
    }

    public void recordCompletion(LlmUsage usage) {
        completionRequests.increment();
        promptTokens.increment(usage.promptTokens());
        completionTokens.increment(usage.completionTokens());
    }
}
