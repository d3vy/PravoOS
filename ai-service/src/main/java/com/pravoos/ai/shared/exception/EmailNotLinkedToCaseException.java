package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class EmailNotLinkedToCaseException extends PravoosException {

  public EmailNotLinkedToCaseException(UUID emailId) {
    super(
        "Письмо " + emailId + " не привязано к делу — вложения сохранять некуда",
        HttpStatus.CONFLICT,
        "EMAIL_NOT_LINKED_TO_CASE");
  }
}
