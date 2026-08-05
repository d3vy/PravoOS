package com.pravoos.ai.shared.exception;

public class CourtIntegrationException extends RuntimeException {

  public CourtIntegrationException(String message) {
    super(message);
  }

  public CourtIntegrationException(String message, Throwable cause) {
    super(message, cause);
  }
}
