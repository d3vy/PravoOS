package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class DocumentNotFoundException extends PravoosException {

  public DocumentNotFoundException(UUID id) {
    super("Document not found: " + id, HttpStatus.NOT_FOUND);
  }
}
