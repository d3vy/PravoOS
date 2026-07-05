package com.pravoos.ai.core.internal.llm;

import com.pravoos.ai.core.internal.llm.dto.LlmMessage;

import java.util.List;

public interface LlmClient {

    LlmResult complete(String systemPrompt, List<LlmMessage> history, String userMessage);

    float[] embed(String text);

    EmbeddingResult embedBatch(List<String> texts);
}
