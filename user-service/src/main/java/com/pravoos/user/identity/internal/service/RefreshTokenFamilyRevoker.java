package com.pravoos.user.identity.internal.service;

import com.pravoos.user.identity.internal.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RefreshTokenFamilyRevoker {

  private final RefreshTokenRepository refreshTokenRepository;

  public RefreshTokenFamilyRevoker(RefreshTokenRepository refreshTokenRepository) {
    this.refreshTokenRepository = refreshTokenRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int revokeAllActive(UUID userId) {
    return refreshTokenRepository.revokeAllActiveByUserId(userId, LocalDateTime.now());
  }
}
