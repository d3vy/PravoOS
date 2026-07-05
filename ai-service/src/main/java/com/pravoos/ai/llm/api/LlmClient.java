package com.pravoos.ai.llm.api;

import com.pravoos.ai.llm.api.LlmMessage;

import java.util.List;

public interface LlmClient {

    LlmResult complete(String systemPrompt, List<LlmMessage> history, String userMessage);

    float[] embed(String text);

    EmbeddingResult embedBatch(List<String> texts);
}
