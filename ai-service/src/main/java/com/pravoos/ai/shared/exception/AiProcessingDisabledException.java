package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class AiProcessingDisabledException extends PravoosException {

  public AiProcessingDisabledException() {
    super(
        "ИИ-ассистент отключён в настройках приватности."
            + " Включите его в разделе «Мои данные», чтобы продолжить.",
        HttpStatus.FORBIDDEN,
        "AI_PROCESSING_DISABLED");
  }
}
