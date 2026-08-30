package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class LlmQuotaExceededException extends PravoosException {

  public enum Scope {
    LAWYER(
        "Достигнут дневной лимит запросов к ИИ-ассистенту. Попробуйте позже или обратитесь в поддержку.",
        "LLM_QUOTA_EXCEEDED"),
    ORG(
        "Достигнут дневной лимит запросов к ИИ-ассистенту по организации. Попробуйте позже или обратитесь в поддержку.",
        "LLM_ORG_QUOTA_EXCEEDED");

    private final String message;
    private final String code;

    Scope(String message, String code) {
      this.message = message;
      this.code = code;
    }
  }

  public LlmQuotaExceededException() {
    this(Scope.LAWYER);
  }

  public LlmQuotaExceededException(Scope scope) {
    super(scope.message, HttpStatus.TOO_MANY_REQUESTS, scope.code);
  }
}
