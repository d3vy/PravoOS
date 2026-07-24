package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class LlmException extends PravoosException {

  public LlmException(String message) {
    super(message, HttpStatus.SERVICE_UNAVAILABLE);
  }
}
