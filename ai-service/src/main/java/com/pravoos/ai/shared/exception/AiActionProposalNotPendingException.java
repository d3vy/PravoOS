package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class AiActionProposalNotPendingException extends PravoosException {

  public AiActionProposalNotPendingException(UUID id) {
    super("AI action proposal is no longer pending: " + id, HttpStatus.CONFLICT);
  }
}
