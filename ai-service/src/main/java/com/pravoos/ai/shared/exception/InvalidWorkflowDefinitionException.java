package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class InvalidWorkflowDefinitionException extends PravoosException {

    public InvalidWorkflowDefinitionException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
