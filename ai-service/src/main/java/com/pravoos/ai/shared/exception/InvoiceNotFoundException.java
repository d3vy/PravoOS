package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class InvoiceNotFoundException extends PravoosException {

    public InvoiceNotFoundException(UUID id) {
        super("Invoice not found: " + id, HttpStatus.NOT_FOUND);
    }
}
