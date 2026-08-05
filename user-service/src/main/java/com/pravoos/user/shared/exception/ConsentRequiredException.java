package com.pravoos.user.shared.exception;

import org.springframework.http.HttpStatus;

public class ConsentRequiredException extends PravoosException {

  public ConsentRequiredException() {
    super(
        "Без согласия на обработку персональных данных регистрация невозможна",
        HttpStatus.BAD_REQUEST,
        "CONSENT_REQUIRED");
  }
}
