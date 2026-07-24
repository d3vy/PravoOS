package com.pravoos.user.shared.exception;

import org.springframework.http.HttpStatus;

public class ApplicationAlreadyExistsException extends PravoosException {

  public ApplicationAlreadyExistsException() {
    super(
        "Заявка с такой почтой уже находится на рассмотрении",
        HttpStatus.CONFLICT,
        "APPLICATION_PENDING");
  }
}
