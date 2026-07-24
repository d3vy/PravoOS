package com.pravoos.user.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class ApplicationStatusException extends PravoosException {

  public ApplicationStatusException(UUID id, Object currentStatus) {
    super(
        "Application " + id + " cannot be reviewed, current status: " + currentStatus,
        HttpStatus.CONFLICT);
  }
}
