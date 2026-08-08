package com.pravoos.user.identity.internal.service;

import com.pravoos.user.identity.internal.config.JwtProperties;
import com.pravoos.user.identity.internal.dto.IssuedRefreshToken;
import com.pravoos.user.identity.internal.dto.SessionResponse;
import com.pravoos.user.identity.internal.model.entity.RefreshToken;
import com.pravoos.user.identity.internal.repository.RefreshTokenRepository;
import com.pravoos.user.shared.exception.InvalidRefreshTokenException;
import com.pravoos.user.shared.security.TokenHasher;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
  private static final int TOKEN_BYTE_LENGTH = 32;

  private final RefreshTokenRepository refreshTokenRepository;
  private final RefreshTokenFamilyRevoker refreshTokenFamilyRevoker;
  private final TokenHasher tokenHasher;
  private final SecureRandom secureRandom = new SecureRandom();
  private final long refreshExpirationMs;

  public RefreshTokenService(
      RefreshTokenRepository refreshTokenRepository,
      RefreshTokenFamilyRevoker refreshTokenFamilyRevoker,
      TokenHasher tokenHasher,
      JwtProperties jwtProperties) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.refreshTokenFamilyRevoker = refreshTokenFamilyRevoker;
    this.tokenHasher = tokenHasher;
    this.refreshExpirationMs = jwtProperties.refreshExpirationMs();
  }

  @Transactional
  public IssuedRefreshToken issue(UUID userId, String ipAddress, String userAgent) {
    String rawToken = generateRawToken();
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    RefreshToken refreshToken = new RefreshToken();
    refreshToken.setUserId(userId);
    refreshToken.setTokenHash(tokenHasher.sha256Hex(rawToken));
    refreshToken.setExpiresAt(now.plus(refreshExpirationMs, ChronoUnit.MILLIS));
    refreshToken.setIpAddress(ipAddress);
    refreshToken.setUserAgent(userAgent);
    refreshToken.setLastUsedAt(now);
    RefreshToken saved = refreshTokenRepository.save(refreshToken);
    return new IssuedRefreshToken(rawToken, saved.getId());
  }

  @Transactional(readOnly = true)
  public boolean isKnownDevice(UUID userId, String ipAddress) {
    return ipAddress != null
        && refreshTokenRepository.existsByUserIdAndIpAddressAndRevokedAtIsNullAndExpiresAtAfter(
            userId, ipAddress, LocalDateTime.now(ZoneOffset.UTC));
  }

  @Transactional(readOnly = true)
  public List<SessionResponse> listActiveSessions(UUID userId, UUID currentSessionId) {
    return refreshTokenRepository
        .findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            userId, LocalDateTime.now(ZoneOffset.UTC))
        .stream()
        .map(
            token ->
                new SessionResponse(
                    token.getId(),
                    token.getIpAddress(),
                    token.getUserAgent(),
                    token.getCreatedAt(),
                    token.getLastUsedAt(),
                    token.getId().equals(currentSessionId)))
        .toList();
  }

  @Transactional
  public void revokeSession(UUID userId, UUID sessionId) {
    refreshTokenRepository
        .findByIdAndUserId(sessionId, userId)
        .filter(token -> token.getRevokedAt() == null)
        .ifPresent(token -> token.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC)));
  }

  @Transactional
  public UUID rotate(String rawToken) {
    RefreshToken stored =
        refreshTokenRepository
            .findByTokenHash(tokenHasher.sha256Hex(rawToken))
            .orElseThrow(InvalidRefreshTokenException::new);

    if (stored.getRevokedAt() != null) {
      int revoked = refreshTokenFamilyRevoker.revokeAllActive(stored.getUserId());
      log.warn(
          "Refresh token reuse detected for user {}; revoked {} active tokens",
          stored.getUserId(),
          revoked);
      throw new InvalidRefreshTokenException();
    }

    if (stored.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
      stored.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));
      refreshTokenRepository.save(stored);
      throw new InvalidRefreshTokenException();
    }

    stored.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));
    try {
      refreshTokenRepository.saveAndFlush(stored);
    } catch (OptimisticLockingFailureException ex) {
      log.warn("Concurrent refresh token rotation detected for user {}", stored.getUserId());
      throw new InvalidRefreshTokenException();
    }
    return stored.getUserId();
  }

  @Transactional
  public void revoke(String rawToken) {
    refreshTokenRepository
        .findByTokenHash(tokenHasher.sha256Hex(rawToken))
        .filter(token -> token.getRevokedAt() == null)
        .ifPresent(token -> token.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC)));
  }

  @Scheduled(cron = "0 0 3 * * *")
  @SchedulerLock(name = "RefreshTokenService_purgeExpiredTokens", lockAtMostFor = "PT10M")
  @Transactional
  public void purgeExpiredTokens() {
    int deleted = refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now(ZoneOffset.UTC));
    if (deleted > 0) {
      log.info("Purged {} expired refresh tokens", deleted);
    }
  }

  private String generateRawToken() {
    byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
    secureRandom.nextBytes(randomBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }
}
