package com.pravoos.ai.core.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AiToolContext(
    UUID userId,
    List<UUID> orgIds,
    AiActorRole role,
    String conversationId,
    LocalDateTime turnStartedAt) {

  public AiToolContext {
    if (userId == null) {
      throw new IllegalArgumentException("userId is required");
    }
    if (role == null) {
      throw new IllegalArgumentException("role is required");
    }
    orgIds = orgIds == null ? List.of() : List.copyOf(orgIds);
  }

  public AiToolContext(UUID userId, List<UUID> orgIds, AiActorRole role) {
    this(userId, orgIds, role, null, null);
  }

  public AiToolContext(UUID userId, List<UUID> orgIds, AiActorRole role, String conversationId) {
    this(userId, orgIds, role, conversationId, null);
  }

  public boolean isLawyer() {
    return role == AiActorRole.LAWYER;
  }

  public UUID singleOrgId() {
    return orgIds.size() == 1 ? orgIds.getFirst() : null;
  }
}
