package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class CaseExportException extends PravoosException {

    public CaseExportException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
        initCause(cause);
    }
}
