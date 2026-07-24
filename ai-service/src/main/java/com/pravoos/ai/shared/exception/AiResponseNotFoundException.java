package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class AiResponseNotFoundException extends PravoosException {

  public AiResponseNotFoundException(UUID id) {
    super("AI response not found: " + id, HttpStatus.NOT_FOUND);
  }
}
