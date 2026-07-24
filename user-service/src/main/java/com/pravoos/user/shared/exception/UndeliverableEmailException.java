package com.pravoos.user.shared.exception;

import org.springframework.http.HttpStatus;

public class UndeliverableEmailException extends PravoosException {

  public UndeliverableEmailException() {
    super(
        "Похоже, такого почтового домена не существует. Проверьте email.", HttpStatus.BAD_REQUEST);
  }
}
