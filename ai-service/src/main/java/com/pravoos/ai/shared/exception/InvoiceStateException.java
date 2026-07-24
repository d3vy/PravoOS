package com.pravoos.ai.shared.exception;

import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import org.springframework.http.HttpStatus;

public class InvoiceStateException extends PravoosException {

  public InvoiceStateException(InvoiceStatus from, InvoiceStatus to) {
    super(
        "Invoice cannot move from " + from + " to " + to,
        HttpStatus.CONFLICT,
        "INVOICE_INVALID_TRANSITION");
  }

  public InvoiceStateException(String message) {
    super(message, HttpStatus.CONFLICT, "INVOICE_INVALID_STATE");
  }
}
