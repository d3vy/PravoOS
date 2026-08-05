package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class CrossBorderConsentRequiredException extends PravoosException {

  public CrossBorderConsentRequiredException() {
    super(
        "Для работы ИИ-ассистента требуется согласие на трансграничную передачу персональных данных."
            + " Оформите согласие в разделе «Мои данные».",
        HttpStatus.FORBIDDEN,
        "CROSS_BORDER_CONSENT_REQUIRED");
  }
}
