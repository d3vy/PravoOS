package com.pravoos.llm.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
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
}
