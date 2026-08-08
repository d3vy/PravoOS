package com.pravoos.llm.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void mapsStatusAndMessageFromException() {
    PravoosException exception =
        new PravoosException("Провайдер недоступен", HttpStatus.SERVICE_UNAVAILABLE);

    ProblemDetail problem = handler.handlePravoos(exception);

    assertThat(problem.getStatus()).isEqualTo(503);
    assertThat(problem.getDetail()).isEqualTo("Провайдер недоступен");
    assertThat(problem.getProperties()).isNull();
  }

  @Test
  void includesCodeWhenPresent() {
    PravoosException exception =
        new PravoosException("bad secret", HttpStatus.FORBIDDEN, "FORBIDDEN_INTERNAL");

    ProblemDetail problem = handler.handlePravoos(exception);

    assertThat(problem.getStatus()).isEqualTo(403);
    assertThat(problem.getProperties()).containsEntry("code", "FORBIDDEN_INTERNAL");
  }

  @Test
  void mapsLlmExceptionToServiceUnavailable() {
    ProblemDetail problem = handler.handlePravoos(new LlmException("timeout"));

    assertThat(problem.getStatus()).isEqualTo(503);
    assertThat(problem.getDetail()).isEqualTo("timeout");
  }
}
