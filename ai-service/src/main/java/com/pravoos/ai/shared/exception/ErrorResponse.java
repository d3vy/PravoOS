package com.pravoos.ai.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String message, String code, String errorId, LocalDateTime timestamp) {
  public ErrorResponse(String message) {
    this(message, null, null, LocalDateTime.now());
  }

  public ErrorResponse(String message, String code) {
    this(message, code, null, LocalDateTime.now());
  }

  public static ErrorResponse of(String message, String code, String errorId) {
    return new ErrorResponse(message, code, errorId, LocalDateTime.now());
  }
}
