package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class ClientEmailRequiredException extends PravoosException {

  public ClientEmailRequiredException(UUID clientId) {
    super("Client has no email, cannot invite to portal: " + clientId, HttpStatus.BAD_REQUEST);
  }
}
