package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class DocumentSummaryFailedException extends PravoosException {

  public DocumentSummaryFailedException(String message) {
    super(message, HttpStatus.UNPROCESSABLE_ENTITY, "DOCUMENT_SUMMARY_FAILED");
  }
}
