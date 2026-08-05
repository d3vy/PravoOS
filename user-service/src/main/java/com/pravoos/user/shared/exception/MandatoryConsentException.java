package com.pravoos.user.shared.exception;

import org.springframework.http.HttpStatus;

public class MandatoryConsentException extends PravoosException {

  public MandatoryConsentException() {
    super(
        "Это согласие обязательно для работы сервиса — его отзыв означает удаление аккаунта",
        HttpStatus.CONFLICT,
        "MANDATORY_CONSENT");
  }
}
