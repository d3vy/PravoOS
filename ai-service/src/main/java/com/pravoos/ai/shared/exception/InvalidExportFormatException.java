package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class InvalidExportFormatException extends PravoosException {

  public InvalidExportFormatException(String value) {
    super(
        "Unsupported export format: " + value + " (expected docx or pdf)", HttpStatus.BAD_REQUEST);
  }
}
