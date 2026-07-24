package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class DraftEditingException extends PravoosException {

  public DraftEditingException(String message) {
    super(message, HttpStatus.UNPROCESSABLE_ENTITY, "DRAFT_EDITING_FAILED");
  }
}
