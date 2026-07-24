package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.SavedViewScope;
import com.pravoos.ai.practice.internal.model.entity.SavedView;
import java.time.LocalDateTime;
import java.util.UUID;

public record SavedViewResponse(
    UUID id,
    SavedViewScope scope,
    String name,
    String config,
    boolean sharedWithTeam,
    UUID orgId,
    boolean owned,
    LocalDateTime updatedAt) {
  public static SavedViewResponse from(SavedView view, UUID currentLawyerId) {
    return new SavedViewResponse(
        view.getId(),
        view.getScope(),
        view.getName(),
        view.getConfig(),
        view.isSharedWithTeam(),
        view.getOrgId(),
        view.getLawyerId().equals(currentLawyerId),
        view.getUpdatedAt());
  }
}
