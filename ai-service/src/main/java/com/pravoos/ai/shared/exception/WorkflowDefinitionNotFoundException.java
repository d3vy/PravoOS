package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class WorkflowDefinitionNotFoundException extends PravoosException {

  public WorkflowDefinitionNotFoundException(UUID id) {
    super("Workflow definition not found: " + id, HttpStatus.NOT_FOUND);
  }
}
