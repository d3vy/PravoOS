package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.repository.jpa.CaseMessageRepository.ThreadView;
import com.pravoos.ai.shared.model.enums.MessageAuthorRole;
import com.pravoos.ai.shared.util.TextPreview;
import java.time.LocalDateTime;
import java.util.UUID;

public record CaseThreadResponse(
    UUID caseId,
    String caseTitle,
    String clientName,
    String lastMessagePreview,
    MessageAuthorRole lastAuthorRole,
    LocalDateTime lastMessageAt,
    long unreadCount) {
  private static final int PREVIEW_MAX_LENGTH = 140;

  public static CaseThreadResponse from(ThreadView view) {
    return new CaseThreadResponse(
        view.getCaseId(),
        view.getCaseTitle(),
        view.getClientName(),
        preview(view.getLastBody()),
        MessageAuthorRole.valueOf(view.getLastAuthorRole()),
        view.getLastCreatedAt(),
        view.getUnreadCount());
  }

  private static String preview(String body) {
    return TextPreview.clamp(body.strip(), PREVIEW_MAX_LENGTH);
  }
}
