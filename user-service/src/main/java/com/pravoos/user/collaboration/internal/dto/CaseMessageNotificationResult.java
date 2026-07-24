package com.pravoos.user.collaboration.internal.dto;

import java.util.UUID;

public record CaseMessageNotificationResult(
    UUID recipientUserId, Long telegramChatId, boolean pushEnabled) {
  public static CaseMessageNotificationResult none() {
    return new CaseMessageNotificationResult(null, null, false);
  }
}
