package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class ClientContactNotFoundException extends PravoosException {

  public ClientContactNotFoundException(UUID id) {
    super("Client contact not found: " + id, HttpStatus.NOT_FOUND);
  }
}
