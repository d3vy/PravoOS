package com.pravoos.user.service;

import com.pravoos.user.config.JwtProperties;
import com.pravoos.user.exception.InvalidRefreshTokenException;
import com.pravoos.user.model.entity.RefreshToken;
import com.pravoos.user.repository.RefreshTokenRepository;
import com.pravoos.user.security.TokenHasher;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTE_LENGTH = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenFamilyRevoker refreshTokenFamilyRevoker;
    private final TokenHasher tokenHasher;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long refreshExpirationMs;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               RefreshTokenFamilyRevoker refreshTokenFamilyRevoker,
                               TokenHasher tokenHasher,
                               JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenFamilyRevoker = refreshTokenFamilyRevoker;
        this.tokenHasher = tokenHasher;
        this.refreshExpirationMs = jwtProperties.refreshExpirationMs();
    }

    @Transactional
    public String issue(UUID userId) {
        String rawToken = generateRawToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setTokenHash(tokenHasher.sha256Hex(rawToken));
        refreshToken.setExpiresAt(LocalDateTime.now().plus(refreshExpirationMs, ChronoUnit.MILLIS));
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Transactional
    public UUID rotate(String rawToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHasher.sha256Hex(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (stored.getRevokedAt() != null) {
            int revoked = refreshTokenFamilyRevoker.revokeAllActive(stored.getUserId());
            log.warn("Refresh token reuse detected for user {}; revoked {} active tokens", stored.getUserId(), revoked);
            throw new InvalidRefreshTokenException();
        }

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            stored.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(stored);
            throw new InvalidRefreshTokenException();
        }

        stored.setRevokedAt(LocalDateTime.now());
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
        refreshTokenRepository.findByTokenHash(tokenHasher.sha256Hex(rawToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.setRevokedAt(LocalDateTime.now()));
    }

    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "RefreshTokenService_purgeExpiredTokens", lockAtMostFor = "PT10M")
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
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
