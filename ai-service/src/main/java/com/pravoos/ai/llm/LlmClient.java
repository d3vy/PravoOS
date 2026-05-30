package com.pravoos.ai.llm;

import com.pravoos.ai.llm.dto.LlmMessage;

import java.util.List;

public interface LlmClient {

    String complete(String systemPrompt, List<LlmMessage> history, String userMessage);

    float[] embed(String text);
}
