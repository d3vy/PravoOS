package com.pravoos.user.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class ApplicationNotFoundException extends PravoosException {

  public ApplicationNotFoundException(UUID id) {
    super("Application not found: " + id, HttpStatus.NOT_FOUND);
  }
}
