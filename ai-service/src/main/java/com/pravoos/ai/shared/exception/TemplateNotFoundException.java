package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class TemplateNotFoundException extends PravoosException {

  public TemplateNotFoundException(UUID id) {
    super("Template not found: " + id, HttpStatus.NOT_FOUND);
  }
}
