package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.repository.jpa.CaseMessageRepository.ThreadView;
import com.pravoos.ai.shared.model.enums.MessageAuthorRole;
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
    String normalized = body.strip();
    return normalized.length() <= PREVIEW_MAX_LENGTH
        ? normalized
        : normalized.substring(0, PREVIEW_MAX_LENGTH) + "…";
  }
}
