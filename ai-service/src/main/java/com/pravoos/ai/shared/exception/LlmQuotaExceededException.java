package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class LlmQuotaExceededException extends PravoosException {

  public LlmQuotaExceededException() {
    super(
        "Достигнут дневной лимит запросов к ИИ-ассистенту. Попробуйте позже или обратитесь в поддержку.",
        HttpStatus.TOO_MANY_REQUESTS,
        "LLM_QUOTA_EXCEEDED");
  }
}
