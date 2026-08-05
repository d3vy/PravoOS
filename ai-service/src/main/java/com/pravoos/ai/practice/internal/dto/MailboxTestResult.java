package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.shared.model.enums.MailboxStatus;

public record MailboxTestResult(boolean success, MailboxStatus status, String message) {

  public static MailboxTestResult ok() {
    return new MailboxTestResult(true, MailboxStatus.OK, null);
  }

  public static MailboxTestResult failed(String message) {
    return new MailboxTestResult(false, MailboxStatus.ERROR, message);
  }
}
