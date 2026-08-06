package com.pravoos.user.identity.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.pravoos.user.identity.internal.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenFamilyRevokerTest {

  @Mock private RefreshTokenRepository refreshTokenRepository;

  private RefreshTokenFamilyRevoker revoker;

  @BeforeEach
  void setUp() {
    revoker = new RefreshTokenFamilyRevoker(refreshTokenRepository);
  }

  @Test
  void revokeAllActive_delegatesToRepositoryAndReturnsCount() {
    UUID userId = UUID.randomUUID();
    when(refreshTokenRepository.revokeAllActiveByUserId(eq(userId), any(LocalDateTime.class)))
        .thenReturn(3);

    int revoked = revoker.revokeAllActive(userId);

    assertThat(revoked).isEqualTo(3);
  }

  @Test
  void revokeAllActive_returnsZeroWhenNoActiveTokens() {
    UUID userId = UUID.randomUUID();
    when(refreshTokenRepository.revokeAllActiveByUserId(eq(userId), any(LocalDateTime.class)))
        .thenReturn(0);

    int revoked = revoker.revokeAllActive(userId);

    assertThat(revoked).isZero();
  }
}
