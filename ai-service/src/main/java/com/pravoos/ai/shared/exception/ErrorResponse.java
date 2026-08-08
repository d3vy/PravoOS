package com.pravoos.ai.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String message, String code, String errorId, LocalDateTime timestamp) {
  public ErrorResponse(String message) {
    this(message, null, null, LocalDateTime.now(ZoneOffset.UTC));
  }

  public ErrorResponse(String message, String code) {
    this(message, code, null, LocalDateTime.now(ZoneOffset.UTC));
  }

  public static ErrorResponse of(String message, String code, String errorId) {
    return new ErrorResponse(message, code, errorId, LocalDateTime.now(ZoneOffset.UTC));
  }
}
