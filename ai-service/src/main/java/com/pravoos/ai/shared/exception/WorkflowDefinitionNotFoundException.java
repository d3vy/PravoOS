package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class WorkflowDefinitionNotFoundException extends PravoosException {

    public WorkflowDefinitionNotFoundException(UUID id) {
        super("Workflow definition not found: " + id, HttpStatus.NOT_FOUND);
    }
}
