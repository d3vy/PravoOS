package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class MailboxLimitExceededException extends PravoosException {

  public MailboxLimitExceededException(int limit) {
    super(
        "Достигнут лимит подключённых ящиков: " + limit,
        HttpStatus.CONFLICT,
        "MAILBOX_LIMIT_EXCEEDED");
  }
}
