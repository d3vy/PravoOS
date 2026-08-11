package com.pravoos.llm.exception;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(PravoosException.class)
  public ProblemDetail handlePravoos(PravoosException exception) {
    log.warn("Handled application error: {}", exception.getMessage());
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
    if (exception.getCode() != null) {
      problem.setProperty("code", exception.getCode());
    }
    return problem;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleGeneral(Exception exception) {
    if (exception instanceof ErrorResponse springError) {
      log.warn("Handled request error: {}", exception.getMessage());
      return springError.getBody();
    }
    String errorId = currentErrorId();
    log.error("Unexpected error [errorId={}]", errorId, exception);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервиса");
    problem.setProperty("code", "INTERNAL_ERROR");
    problem.setProperty("errorId", errorId);
    return problem;
  }

  private String currentErrorId() {
    String requestId = MDC.get("requestId");
    return requestId != null ? requestId : UUID.randomUUID().toString();
  }
}
