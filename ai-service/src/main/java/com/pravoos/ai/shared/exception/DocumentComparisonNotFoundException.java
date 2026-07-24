package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class DocumentComparisonNotFoundException extends PravoosException {

  public DocumentComparisonNotFoundException(UUID comparisonId) {
    super(
        "Сравнение версий не найдено: " + comparisonId,
        HttpStatus.NOT_FOUND,
        "COMPARISON_NOT_FOUND");
  }
}
