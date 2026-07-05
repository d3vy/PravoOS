package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class WorkflowNotFoundException extends PravoosException {

    public WorkflowNotFoundException(String id) {
        super("Workflow not found: " + id, HttpStatus.NOT_FOUND);
    }
}
