package com.pravoos.user.identity.internal.service;

import com.pravoos.user.identity.api.UserSessionQuery;
import com.pravoos.user.identity.api.UserSessionSnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UserSessionQueryAdapter implements UserSessionQuery {

  private final RefreshTokenService refreshTokenService;

  public UserSessionQueryAdapter(RefreshTokenService refreshTokenService) {
    this.refreshTokenService = refreshTokenService;
  }

  @Override
  public List<UserSessionSnapshot> activeSessions(UUID userId) {
    return refreshTokenService.listActiveSessions(userId).stream()
        .map(
            session ->
                new UserSessionSnapshot(
                    session.ipAddress(),
                    session.userAgent(),
                    session.createdAt(),
                    session.lastUsedAt()))
        .toList();
  }
}
