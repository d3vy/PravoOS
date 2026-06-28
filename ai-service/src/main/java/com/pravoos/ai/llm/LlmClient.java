package com.pravoos.ai.llm;

import com.pravoos.ai.llm.dto.LlmMessage;

import java.util.List;

public interface LlmClient {

    LlmResult complete(String systemPrompt, List<LlmMessage> history, String userMessage);

    float[] embed(String text);

    List<float[]> embedBatch(List<String> texts);
}
