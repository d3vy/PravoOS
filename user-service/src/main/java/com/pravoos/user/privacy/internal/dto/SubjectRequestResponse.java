package com.pravoos.user.privacy.internal.dto;

import com.pravoos.user.privacy.internal.model.entity.SubjectRequest;
import com.pravoos.user.privacy.internal.model.enums.SubjectRequestStatus;
import com.pravoos.user.privacy.internal.model.enums.SubjectRequestType;
import java.time.LocalDateTime;
import java.util.UUID;

public record SubjectRequestResponse(
    UUID id,
    UUID userId,
    String subjectRef,
    SubjectRequestType type,
    SubjectRequestStatus status,
    LocalDateTime requestedAt,
    LocalDateTime dueAt,
    LocalDateTime completedAt,
    String note) {

  public static SubjectRequestResponse from(SubjectRequest request) {
    return new SubjectRequestResponse(
        request.getId(),
        request.getUserId(),
        request.getSubjectRef(),
        request.getType(),
        request.getStatus(),
        request.getRequestedAt(),
        request.getDueAt(),
        request.getCompletedAt(),
        request.getNote());
  }
}
