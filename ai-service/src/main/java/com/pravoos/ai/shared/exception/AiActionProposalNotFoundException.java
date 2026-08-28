package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class AiActionProposalNotFoundException extends PravoosException {

  public AiActionProposalNotFoundException(UUID id) {
    super("AI action proposal not found: " + id, HttpStatus.NOT_FOUND);
  }
}
