package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class MailboxHostNotAllowedException extends PravoosException {

  public MailboxHostNotAllowedException() {
    super(
        "IMAP-хост недоступен: укажите публичный почтовый сервер провайдера",
        HttpStatus.BAD_REQUEST,
        "MAILBOX_HOST_NOT_ALLOWED");
  }
}
