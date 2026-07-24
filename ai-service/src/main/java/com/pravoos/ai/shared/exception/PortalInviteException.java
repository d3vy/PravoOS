package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class PortalInviteException extends PravoosException {

  public PortalInviteException(UUID clientId) {
    super("Failed to create portal invite for client: " + clientId, HttpStatus.BAD_GATEWAY);
  }
}
