package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class MailboxConnectionException extends PravoosException {

  public MailboxConnectionException(String message, Throwable cause) {
    super(message, HttpStatus.BAD_GATEWAY, "MAILBOX_CONNECTION_FAILED");
    initCause(cause);
  }
}
