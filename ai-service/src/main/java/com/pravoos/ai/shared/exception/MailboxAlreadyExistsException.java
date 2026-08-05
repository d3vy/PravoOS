package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class MailboxAlreadyExistsException extends PravoosException {

  public MailboxAlreadyExistsException(String emailAddress) {
    super("Ящик уже подключён: " + emailAddress, HttpStatus.CONFLICT, "MAILBOX_ALREADY_EXISTS");
  }
}
