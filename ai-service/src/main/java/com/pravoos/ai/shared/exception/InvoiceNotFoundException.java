package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class InvoiceNotFoundException extends PravoosException {

  public InvoiceNotFoundException(UUID id) {
    super("Invoice not found: " + id, HttpStatus.NOT_FOUND);
  }
}
