package com.pravoos.ai.recyclebin.internal.dto;

import com.pravoos.ai.recyclebin.api.DeletionRole;
import com.pravoos.ai.recyclebin.api.RecycleBinArea;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.recyclebin.internal.model.DeletedEntry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

public record RecycleBinEntryResponse(
    UUID id,
    UUID orgId,
    UUID ownerId,
    RecycleBinEntityType entityType,
    String entityId,
    String title,
    RecycleBinArea area,
    UUID deletedBy,
    DeletionRole deletedByRole,
    LocalDateTime deletedAt,
    LocalDateTime purgeAfter,
    long daysUntilPurge,
    UUID cascadeGroupId,
    boolean cascadeRoot,
    long nestedCount,
    Map<String, Object> payload) {

  public static RecycleBinEntryResponse from(DeletedEntry entry, long nestedCount) {
    return new RecycleBinEntryResponse(
        entry.getId(),
        entry.getOrgId(),
        entry.getOwnerId(),
        entry.getEntityType(),
        entry.getEntityId(),
        entry.getTitle(),
        entry.getArea(),
        entry.getDeletedBy(),
        entry.getDeletedByRole(),
        entry.getDeletedAt(),
        entry.getPurgeAfter(),
        daysUntilPurge(entry.getPurgeAfter()),
        entry.getCascadeGroupId(),
        entry.isCascadeRoot(),
        nestedCount,
        entry.getPayload());
  }

  private static long daysUntilPurge(LocalDateTime purgeAfter) {
    Duration remaining = Duration.between(LocalDateTime.now(ZoneOffset.UTC), purgeAfter);
    return remaining.isNegative() ? 0 : remaining.toDays();
  }
}
