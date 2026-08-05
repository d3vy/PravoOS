package com.pravoos.user.identity.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.user.identity.api.UserSessionSnapshot;
import com.pravoos.user.identity.internal.dto.SessionResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserSessionQueryAdapterTest {

  @Mock private RefreshTokenService refreshTokenService;

  @Test
  void mapsRefreshTokenSessionsToSnapshots() {
    UUID userId = UUID.randomUUID();
    LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
    LocalDateTime lastUsedAt = LocalDateTime.of(2026, 1, 2, 11, 30);
    SessionResponse session =
        new SessionResponse(UUID.randomUUID(), "10.0.0.1", "Mozilla/5.0", createdAt, lastUsedAt);
    when(refreshTokenService.listActiveSessions(userId)).thenReturn(List.of(session));

    UserSessionQueryAdapter target = new UserSessionQueryAdapter(refreshTokenService);
    List<UserSessionSnapshot> result = target.activeSessions(userId);

    assertThat(result)
        .containsExactly(new UserSessionSnapshot("10.0.0.1", "Mozilla/5.0", createdAt, lastUsedAt));
    verify(refreshTokenService).listActiveSessions(userId);
  }

  @Test
  void returnsEmptyListWhenNoActiveSessions() {
    UUID userId = UUID.randomUUID();
    when(refreshTokenService.listActiveSessions(userId)).thenReturn(List.of());

    UserSessionQueryAdapter target = new UserSessionQueryAdapter(refreshTokenService);
    List<UserSessionSnapshot> result = target.activeSessions(userId);

    assertThat(result).isEmpty();
  }

  @Test
  void mapsMultipleSessionsPreservingOrder() {
    UUID userId = UUID.randomUUID();
    SessionResponse first =
        new SessionResponse(
            UUID.randomUUID(),
            "10.0.0.1",
            "Chrome",
            LocalDateTime.of(2026, 1, 1, 9, 0),
            LocalDateTime.of(2026, 1, 1, 9, 5));
    SessionResponse second =
        new SessionResponse(
            UUID.randomUUID(),
            "10.0.0.2",
            "Safari",
            LocalDateTime.of(2026, 1, 2, 9, 0),
            LocalDateTime.of(2026, 1, 2, 9, 5));
    when(refreshTokenService.listActiveSessions(userId)).thenReturn(List.of(first, second));

    UserSessionQueryAdapter target = new UserSessionQueryAdapter(refreshTokenService);
    List<UserSessionSnapshot> result = target.activeSessions(userId);

    assertThat(result)
        .containsExactly(
            new UserSessionSnapshot("10.0.0.1", "Chrome", first.createdAt(), first.lastUsedAt()),
            new UserSessionSnapshot("10.0.0.2", "Safari", second.createdAt(), second.lastUsedAt()));
  }
}
