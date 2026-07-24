package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class DocumentProcessingException extends PravoosException {

  public DocumentProcessingException(String message) {
    super(message, HttpStatus.UNPROCESSABLE_ENTITY);
  }
}
