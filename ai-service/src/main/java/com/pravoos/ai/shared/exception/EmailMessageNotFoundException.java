package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class EmailMessageNotFoundException extends PravoosException {

  public EmailMessageNotFoundException(UUID id) {
    super("Email message not found: " + id, HttpStatus.NOT_FOUND);
  }
}
