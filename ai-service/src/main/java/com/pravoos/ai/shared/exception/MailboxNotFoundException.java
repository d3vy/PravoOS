package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class MailboxNotFoundException extends PravoosException {

  public MailboxNotFoundException(UUID id) {
    super("Mailbox not found: " + id, HttpStatus.NOT_FOUND);
  }
}
