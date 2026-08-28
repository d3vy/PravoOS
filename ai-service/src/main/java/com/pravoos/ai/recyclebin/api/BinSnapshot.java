package com.pravoos.ai.recyclebin.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record BinSnapshot(
    RecycleBinEntityType entityType,
    String entityId,
    String title,
    UUID ownerId,
    UUID orgId,
    Map<String, Object> payload) {

  public BinSnapshot {
    payload = withoutNullValues(payload);
  }

  private static Map<String, Object> withoutNullValues(Map<String, Object> payload) {
    if (payload == null || payload.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> retained = new LinkedHashMap<>();
    payload.forEach(
        (key, value) -> {
          if (value != null) {
            retained.put(key, value);
          }
        });
    return Map.copyOf(retained);
  }
}
