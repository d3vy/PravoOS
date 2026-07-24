package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class OrgMembershipCheckException extends PravoosException {

  public OrgMembershipCheckException(UUID orgId) {
    super(
        "Не удалось проверить членство в организации " + orgId + ", попробуйте позже",
        HttpStatus.SERVICE_UNAVAILABLE,
        "ORG_MEMBERSHIP_CHECK_FAILED");
  }
}
