package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class TimeEntryNotFoundException extends PravoosException {

  public TimeEntryNotFoundException(UUID id) {
    super("Time entry not found: " + id, HttpStatus.NOT_FOUND);
  }
}
