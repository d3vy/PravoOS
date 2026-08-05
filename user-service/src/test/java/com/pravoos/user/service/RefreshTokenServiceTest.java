package com.pravoos.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.pravoos.user.identity.internal.config.JwtProperties;
import com.pravoos.user.identity.internal.dto.SessionResponse;
import com.pravoos.user.identity.internal.model.entity.RefreshToken;
import com.pravoos.user.identity.internal.repository.RefreshTokenRepository;
import com.pravoos.user.identity.internal.service.RefreshTokenFamilyRevoker;
import com.pravoos.user.identity.internal.service.RefreshTokenService;
import com.pravoos.user.shared.exception.InvalidRefreshTokenException;
import com.pravoos.user.shared.security.TokenHasher;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  private static final long REFRESH_TTL_MS = 7L * 24 * 60 * 60 * 1000;

  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private RefreshTokenFamilyRevoker refreshTokenFamilyRevoker;

  private final TokenHasher tokenHasher = new TokenHasher();
  private RefreshTokenService service;

  @BeforeEach
  void setUp() {
    JwtProperties jwtProperties = new JwtProperties(null, null, 900_000L, REFRESH_TTL_MS);
    service =
        new RefreshTokenService(
            refreshTokenRepository, refreshTokenFamilyRevoker, tokenHasher, jwtProperties);
  }

  @Test
  void issueStoresHashedTokenAndReturnsRawToken() {
    UUID userId = UUID.randomUUID();
    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);

    String rawToken = service.issue(userId, "203.0.113.7", "JUnit-UA");

    verify(refreshTokenRepository).save(captor.capture());
    RefreshToken saved = captor.getValue();
    assertThat(rawToken).isNotBlank();
    assertThat(saved.getUserId()).isEqualTo(userId);
    assertThat(saved.getTokenHash()).isEqualTo(tokenHasher.sha256Hex(rawToken));
    assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
    assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
    assertThat(saved.getIpAddress()).isEqualTo("203.0.113.7");
    assertThat(saved.getUserAgent()).isEqualTo("JUnit-UA");
    assertThat(saved.getLastUsedAt()).isNotNull();
  }

  @Test
  void rotateReturnsUserIdAndRevokesOldTokenForActiveToken() {
    String raw = "valid-token";
    UUID userId = UUID.randomUUID();
    RefreshToken active = token(userId, null, LocalDateTime.now().plusDays(1));
    when(refreshTokenRepository.findByTokenHash(tokenHasher.sha256Hex(raw)))
        .thenReturn(Optional.of(active));

    UUID result = service.rotate(raw);

    assertThat(result).isEqualTo(userId);
    assertThat(active.getRevokedAt()).isNotNull();
    verify(refreshTokenRepository).saveAndFlush(active);
    verify(refreshTokenFamilyRevoker, never()).revokeAllActive(any());
  }

  @Test
  void rotateThrowsForUnknownToken() {
    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.rotate("nope"))
        .isInstanceOf(InvalidRefreshTokenException.class);
    verify(refreshTokenFamilyRevoker, never()).revokeAllActive(any());
  }

  @Test
  void rotateRevokesWholeFamilyOnReuseOfRevokedToken() {
    String raw = "reused-token";
    UUID userId = UUID.randomUUID();
    RefreshToken revoked =
        token(userId, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusDays(1));
    when(refreshTokenRepository.findByTokenHash(tokenHasher.sha256Hex(raw)))
        .thenReturn(Optional.of(revoked));

    assertThatThrownBy(() -> service.rotate(raw)).isInstanceOf(InvalidRefreshTokenException.class);
    verify(refreshTokenFamilyRevoker).revokeAllActive(userId);
    verify(refreshTokenRepository, never()).saveAndFlush(any());
  }

  @Test
  void rotateRevokesAndThrowsForExpiredToken() {
    String raw = "expired-token";
    UUID userId = UUID.randomUUID();
    RefreshToken expired = token(userId, null, LocalDateTime.now().minusMinutes(1));
    when(refreshTokenRepository.findByTokenHash(tokenHasher.sha256Hex(raw)))
        .thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> service.rotate(raw)).isInstanceOf(InvalidRefreshTokenException.class);
    assertThat(expired.getRevokedAt()).isNotNull();
    verify(refreshTokenRepository).save(expired);
    verify(refreshTokenFamilyRevoker, never()).revokeAllActive(any());
  }

  @Test
  void rotateThrowsOnConcurrentRotation() {
    String raw = "racing-token";
    UUID userId = UUID.randomUUID();
    RefreshToken active = token(userId, null, LocalDateTime.now().plusDays(1));
    when(refreshTokenRepository.findByTokenHash(tokenHasher.sha256Hex(raw)))
        .thenReturn(Optional.of(active));
    when(refreshTokenRepository.saveAndFlush(active))
        .thenThrow(new OptimisticLockingFailureException("concurrent"));

    assertThatThrownBy(() -> service.rotate(raw)).isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void revokeMarksActiveTokenRevoked() {
    String raw = "logout-token";
    RefreshToken active = token(UUID.randomUUID(), null, LocalDateTime.now().plusDays(1));
    when(refreshTokenRepository.findByTokenHash(tokenHasher.sha256Hex(raw)))
        .thenReturn(Optional.of(active));

    service.revoke(raw);

    assertThat(active.getRevokedAt()).isNotNull();
  }

  @Test
  void revokeIsNoOpForAlreadyRevokedToken() {
    String raw = "already-out";
    LocalDateTime originalRevokedAt = LocalDateTime.now().minusHours(1);
    RefreshToken revoked =
        token(UUID.randomUUID(), originalRevokedAt, LocalDateTime.now().plusDays(1));
    when(refreshTokenRepository.findByTokenHash(tokenHasher.sha256Hex(raw)))
        .thenReturn(Optional.of(revoked));

    service.revoke(raw);

    assertThat(revoked.getRevokedAt()).isEqualTo(originalRevokedAt);
  }

  @Test
  void listActiveSessionsReturnsMappedSessionsForUser() {
    UUID userId = UUID.randomUUID();
    RefreshToken session = token(userId, null, LocalDateTime.now().plusDays(1));
    session.setId(UUID.randomUUID());
    session.setIpAddress("203.0.113.7");
    session.setUserAgent("JUnit-UA");
    when(refreshTokenRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            eq(userId), any()))
        .thenReturn(List.of(session));

    List<SessionResponse> sessions = service.listActiveSessions(userId);

    assertThat(sessions).hasSize(1);
    SessionResponse response = sessions.get(0);
    assertThat(response.id()).isEqualTo(session.getId());
    assertThat(response.ipAddress()).isEqualTo("203.0.113.7");
    assertThat(response.userAgent()).isEqualTo("JUnit-UA");
  }

  @Test
  void listActiveSessionsReturnsEmptyListWhenNoActiveSessions() {
    UUID userId = UUID.randomUUID();
    when(refreshTokenRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            eq(userId), any()))
        .thenReturn(List.of());

    assertThat(service.listActiveSessions(userId)).isEmpty();
  }

  @Test
  void revokeSessionRevokesMatchingActiveSessionForUser() {
    UUID userId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    RefreshToken session = token(userId, null, LocalDateTime.now().plusDays(1));
    session.setId(sessionId);
    when(refreshTokenRepository.findByIdAndUserId(sessionId, userId))
        .thenReturn(Optional.of(session));

    service.revokeSession(userId, sessionId);

    assertThat(session.getRevokedAt()).isNotNull();
  }

  @Test
  void revokeSessionIsNoOpForAlreadyRevokedSession() {
    UUID userId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    LocalDateTime originalRevokedAt = LocalDateTime.now().minusHours(1);
    RefreshToken session = token(userId, originalRevokedAt, LocalDateTime.now().plusDays(1));
    session.setId(sessionId);
    when(refreshTokenRepository.findByIdAndUserId(sessionId, userId))
        .thenReturn(Optional.of(session));

    service.revokeSession(userId, sessionId);

    assertThat(session.getRevokedAt()).isEqualTo(originalRevokedAt);
  }

  @Test
  void revokeSessionIsNoOpForSessionBelongingToAnotherUser() {
    UUID userId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    when(refreshTokenRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.empty());

    service.revokeSession(userId, sessionId);

    verify(refreshTokenRepository).findByIdAndUserId(sessionId, userId);
  }

  @Test
  void isKnownDeviceReturnsFalseWhenIpAddressIsNull() {
    UUID userId = UUID.randomUUID();

    assertThat(service.isKnownDevice(userId, null)).isFalse();
    verify(refreshTokenRepository, never())
        .existsByUserIdAndIpAddressAndRevokedAtIsNullAndExpiresAtAfter(any(), any(), any());
  }

  private RefreshToken token(UUID userId, LocalDateTime revokedAt, LocalDateTime expiresAt) {
    RefreshToken token = new RefreshToken();
    token.setUserId(userId);
    token.setRevokedAt(revokedAt);
    token.setExpiresAt(expiresAt);
    return token;
  }
}
