package com.pravoos.ai.llm.api;

import java.util.List;
import java.util.function.Consumer;

public interface LlmClient {

  LlmResult complete(String systemPrompt, List<LlmMessage> history, String userMessage);

  LlmResult complete(
      String systemPrompt, List<LlmMessage> history, String userMessage, LlmOptions options);

  default LlmStreamResult streamComplete(
      String systemPrompt,
      List<LlmMessage> history,
      String userMessage,
      Consumer<String> tokenConsumer) {
    return streamComplete(systemPrompt, history, userMessage, LlmOptions.DEFAULT, tokenConsumer);
  }

  LlmStreamResult streamComplete(
      String systemPrompt,
      List<LlmMessage> history,
      String userMessage,
      LlmOptions options,
      Consumer<String> tokenConsumer);

  float[] embed(String text);

  EmbeddingResult embedBatch(List<String> texts);
}
