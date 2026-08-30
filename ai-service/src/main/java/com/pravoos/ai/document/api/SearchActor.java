package com.pravoos.ai.document.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record SearchActor(UUID userId, List<UUID> orgIds) {

  public SearchActor {
    if (userId == null) {
      throw new IllegalArgumentException("userId must not be null for a search actor");
    }
    orgIds =
        orgIds == null ? List.of() : orgIds.stream().filter(Objects::nonNull).distinct().toList();
  }

  public static SearchActor of(UUID userId, List<UUID> orgIds) {
    return new SearchActor(userId, orgIds);
  }
}
