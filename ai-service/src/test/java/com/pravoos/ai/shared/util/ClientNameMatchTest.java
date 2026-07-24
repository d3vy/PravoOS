package com.pravoos.ai.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientNameMatchTest {

  private final UUID ivanov = UUID.randomUUID();
  private final UUID petrov = UUID.randomUUID();
  private final UUID romashka = UUID.randomUUID();

  private Map<UUID, String> names() {
    Map<UUID, String> names = new LinkedHashMap<>();
    names.put(ivanov, "Иванов Иван Иванович");
    names.put(petrov, "Петров Пётр");
    names.put(romashka, "ООО Ромашка");
    return names;
  }

  @Test
  void matchesCaseInsensitiveSubstring() {
    Collection<UUID> matched = ClientNameMatch.matchingIds(names(), "иванов");
    assertThat(matched).containsExactly(ivanov);
  }

  @Test
  void matchesInnerSubstring() {
    Collection<UUID> matched = ClientNameMatch.matchingIds(names(), "ромашка");
    assertThat(matched).containsExactly(romashka);
  }

  @Test
  void returnsSentinelWhenNothingMatches() {
    Collection<UUID> matched = ClientNameMatch.matchingIds(names(), "сидоров");
    assertThat(matched).containsExactly(ClientNameMatch.noMatchSentinel());
  }

  @Test
  void returnsSentinelForBlankQuery() {
    assertThat(ClientNameMatch.matchingIds(names(), "   "))
        .containsExactly(ClientNameMatch.noMatchSentinel());
    assertThat(ClientNameMatch.matchingIds(names(), null))
        .containsExactly(ClientNameMatch.noMatchSentinel());
  }

  @Test
  void returnsSentinelForEmptyClientSet() {
    assertThat(ClientNameMatch.matchingIds(Map.of(), "иванов"))
        .containsExactly(ClientNameMatch.noMatchSentinel());
  }

  @Test
  void sentinelIsAllZeroUuidToNeverCollideWithRealClient() {
    assertThat(ClientNameMatch.noMatchSentinel()).isEqualTo(new UUID(0L, 0L));
  }
}
