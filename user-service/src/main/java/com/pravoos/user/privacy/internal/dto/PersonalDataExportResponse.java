package com.pravoos.user.privacy.internal.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PersonalDataExportResponse(
    UUID userId,
    String email,
    String role,
    String status,
    LocalDateTime registeredAt,
    String fullName,
    String specialization,
    String phone,
    String preferredLanguage,
    boolean telegramLinked,
    List<ConsentResponse> consents,
    List<SessionRecord> sessions,
    List<SubjectRequestResponse> requests,
    String operator,
    LocalDateTime exportedAt) {

  public record SessionRecord(
      String ipAddress, String userAgent, LocalDateTime createdAt, LocalDateTime lastUsedAt) {}
}
