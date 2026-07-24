package com.pravoos.notification.bot;

import java.util.UUID;

public record ApplicationCallbackResult(String message, Outcome outcome, UUID applicationId) {

  public enum Outcome {
    TERMINAL,
    EMAIL_NOT_VERIFIED
  }

  public static ApplicationCallbackResult terminal(String message) {
    return new ApplicationCallbackResult(message, Outcome.TERMINAL, null);
  }

  public static ApplicationCallbackResult emailNotVerified(UUID applicationId) {
    return new ApplicationCallbackResult(
        "Email не подтверждён. Нажмите «Принять без почты», чтобы одобрить заявку.",
        Outcome.EMAIL_NOT_VERIFIED,
        applicationId);
  }
}
