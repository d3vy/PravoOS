package com.pravoos.llm.web.dto;

import com.pravoos.llm.domain.LlmMessage;
import com.pravoos.llm.domain.LlmOptions;
import jakarta.validation.constraints.AssertTrue;
import java.util.List;

public record CompleteRequest(
    String systemPrompt, List<LlmMessage> history, String userMessage, LlmOptions options) {

  @AssertTrue(message = "either userMessage or a non-empty history is required")
  public boolean isConversationPresent() {
    return (userMessage != null && !userMessage.isBlank())
        || (history != null && !history.isEmpty());
  }
}
