package com.pravoos.llm.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.server.ResponseStatusException;

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

  @Test
  void hidesUnexpectedFailureDetailsBehindErrorId() {
    ProblemDetail problem =
        handler.handleGeneral(new IllegalStateException("jdbc://user:secret@db/pravoos"));

    assertThat(problem.getStatus()).isEqualTo(500);
    assertThat(problem.getDetail()).isEqualTo("Внутренняя ошибка сервиса");
    assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
    assertThat(problem.getProperties()).containsKey("errorId");
  }

  @Test
  void keepsSpringRequestErrorStatusInsteadOfMaskingItAsInternal() {
    ProblemDetail problem =
        handler.handleGeneral(
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "userMessage is required"));

    assertThat(problem.getStatus()).isEqualTo(400);
    assertThat(problem.getDetail()).isEqualTo("userMessage is required");
  }
}
