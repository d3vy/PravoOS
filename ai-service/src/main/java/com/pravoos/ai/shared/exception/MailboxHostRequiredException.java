package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class MailboxHostRequiredException extends PravoosException {

  public MailboxHostRequiredException(String emailAddress) {
    super(
        "Для домена адреса " + emailAddress + " нет пресета — укажите IMAP-хост и порт вручную",
        HttpStatus.BAD_REQUEST,
        "MAILBOX_HOST_REQUIRED");
  }
}
