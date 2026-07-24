package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class CaseTaskNotFoundException extends PravoosException {

  public CaseTaskNotFoundException(UUID id) {
    super("Case task not found: " + id, HttpStatus.NOT_FOUND);
  }
}
