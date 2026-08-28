package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class AiWriteActionRateLimitException extends PravoosException {

  public AiWriteActionRateLimitException() {
    super("Too many AI write actions requested, try again later", HttpStatus.TOO_MANY_REQUESTS);
  }
}
