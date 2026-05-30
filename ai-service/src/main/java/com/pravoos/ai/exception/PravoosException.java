package com.pravoos.ai.exception;

import org.springframework.http.HttpStatus;

public class PravoosException extends RuntimeException {

    private final HttpStatus status;

    public PravoosException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
