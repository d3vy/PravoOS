package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class OrganizationAccessException extends PravoosException {

  public OrganizationAccessException(UUID orgId) {
    super("Нет доступа к организации: " + orgId, HttpStatus.FORBIDDEN);
  }
}
