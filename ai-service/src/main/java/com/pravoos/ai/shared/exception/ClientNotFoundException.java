package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class ClientNotFoundException extends PravoosException {

  public ClientNotFoundException(UUID id) {
    super("Client not found: " + id, HttpStatus.NOT_FOUND);
  }
}
