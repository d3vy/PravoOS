package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class MailboxPortNotAllowedException extends PravoosException {

  public MailboxPortNotAllowedException(int port) {
    super(
        "IMAP-порт " + port + " недоступен: разрешены только стандартные порты 143 и 993",
        HttpStatus.BAD_REQUEST,
        "MAILBOX_PORT_NOT_ALLOWED");
  }
}
