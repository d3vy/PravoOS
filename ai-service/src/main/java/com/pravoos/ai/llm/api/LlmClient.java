package com.pravoos.ai.llm.api;

import java.util.List;
import java.util.function.Consumer;

public interface LlmClient {

    LlmResult complete(String systemPrompt, List<LlmMessage> history, String userMessage);

    LlmUsage streamComplete(String systemPrompt, List<LlmMessage> history, String userMessage,
                            Consumer<String> tokenConsumer);

    float[] embed(String text);

    EmbeddingResult embedBatch(List<String> texts);
}
