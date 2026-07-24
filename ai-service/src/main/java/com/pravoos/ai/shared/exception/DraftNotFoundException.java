package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class DraftNotFoundException extends PravoosException {

  public DraftNotFoundException(UUID id) {
    super("Draft not found: " + id, HttpStatus.NOT_FOUND);
  }
}
