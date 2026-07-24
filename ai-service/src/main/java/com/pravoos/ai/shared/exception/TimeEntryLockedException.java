package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class TimeEntryLockedException extends PravoosException {

  public TimeEntryLockedException(UUID id) {
    super(
        "Time entry " + id + " is attached to an invoice and cannot be modified",
        HttpStatus.CONFLICT,
        "TIME_ENTRY_LOCKED");
  }
}
