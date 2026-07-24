package com.pravoos.llm.exception;

import com.pravoos.common.exception.HttpStatusCarrier;
import org.springframework.http.HttpStatus;

public class PravoosException extends RuntimeException implements HttpStatusCarrier {

  private final HttpStatus status;
  private final String code;

  public PravoosException(String message, HttpStatus status) {
    this(message, status, null);
  }

  public PravoosException(String message, HttpStatus status, String code) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  @Override
  public int httpStatusCode() {
    return status.value();
  }
}
