package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class TabularReviewNotFoundException extends PravoosException {

  public TabularReviewNotFoundException(UUID reviewId) {
    super(
        "Табличный разбор не найден: " + reviewId,
        HttpStatus.NOT_FOUND,
        "TABULAR_REVIEW_NOT_FOUND");
  }
}
