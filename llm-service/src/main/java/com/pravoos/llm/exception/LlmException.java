package com.pravoos.llm.exception;

import org.springframework.http.HttpStatus;

public class LlmException extends PravoosException {

  public LlmException(String message) {
    super(message, HttpStatus.SERVICE_UNAVAILABLE);
  }
}
