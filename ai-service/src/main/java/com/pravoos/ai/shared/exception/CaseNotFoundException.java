package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class CaseNotFoundException extends PravoosException {

  public CaseNotFoundException(UUID id) {
    super("Case not found: " + id, HttpStatus.NOT_FOUND);
  }
}
