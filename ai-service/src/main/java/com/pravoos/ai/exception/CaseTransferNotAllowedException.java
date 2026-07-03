package com.pravoos.ai.exception;

import org.springframework.http.HttpStatus;

public class CaseTransferNotAllowedException extends PravoosException {

    public CaseTransferNotAllowedException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "CASE_TRANSFER_NOT_ALLOWED");
    }
}
