package com.pravoos.ai.shared.util;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ClientNameMatch {

  private static final UUID NO_MATCH_SENTINEL = new UUID(0L, 0L);

  private ClientNameMatch() {}

  public static UUID noMatchSentinel() {
    return NO_MATCH_SENTINEL;
  }

  public static Collection<UUID> matchingIds(Map<UUID, String> namesById, String query) {
    String normalized = normalize(query);
    if (normalized.isEmpty() || namesById.isEmpty()) {
      return List.of(NO_MATCH_SENTINEL);
    }
    List<UUID> matches =
        namesById.entrySet().stream()
            .filter(entry -> matches(entry.getValue(), normalized))
            .map(Map.Entry::getKey)
            .toList();
    return matches.isEmpty() ? List.of(NO_MATCH_SENTINEL) : matches;
  }

  private static boolean matches(String name, String normalizedQuery) {
    return name != null && name.toLowerCase(Locale.ROOT).contains(normalizedQuery);
  }

  private static String normalize(String query) {
    return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
  }
}
