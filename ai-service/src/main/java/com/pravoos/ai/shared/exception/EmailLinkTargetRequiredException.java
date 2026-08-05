package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class EmailLinkTargetRequiredException extends PravoosException {

  public EmailLinkTargetRequiredException() {
    super(
        "Either caseId or clientId is required to link an email",
        HttpStatus.BAD_REQUEST,
        "EMAIL_LINK_TARGET_REQUIRED");
  }
}
