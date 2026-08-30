package com.pravoos.user.identity.internal.service;

import com.pravoos.user.identity.internal.config.MfaProperties;
import com.pravoos.user.shared.exception.MfaException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class MfaChallengeService {

  private static final Logger log = LoggerFactory.getLogger(MfaChallengeService.class);
  private static final String CHALLENGE_KEY_PREFIX = "mfa_challenge:";
  private static final String ATTEMPTS_KEY_PREFIX = "mfa_attempts:";
  private static final int TOKEN_BYTE_LENGTH = 32;

  private final StringRedisTemplate redisTemplate;
  private final MfaProperties properties;
  private final SecureRandom secureRandom = new SecureRandom();

  public MfaChallengeService(StringRedisTemplate redisTemplate, MfaProperties properties) {
    this.redisTemplate = redisTemplate;
    this.properties = properties;
  }

  public String createChallenge(UUID userId) {
    String token = generateToken();
    try {
      redisTemplate
          .opsForValue()
          .set(challengeKey(token), userId.toString(), properties.challengeTtl());
    } catch (DataAccessException ex) {
      log.error("Redis unavailable while creating MFA challenge for user {}", userId, ex);
      throw MfaException.challengeStoreUnavailable();
    }
    return token;
  }

  public UUID resolve(String token) {
    String userId;
    try {
      userId = redisTemplate.opsForValue().get(challengeKey(token));
    } catch (DataAccessException ex) {
      log.error("Redis unavailable while resolving MFA challenge, failing closed", ex);
      throw MfaException.challengeStoreUnavailable();
    }
    if (userId == null) {
      throw MfaException.invalidChallenge();
    }
    return UUID.fromString(userId);
  }

  public void registerFailedAttempt(String token) {
    String attemptsKey = attemptsKey(token);
    Long attempts;
    try {
      attempts = redisTemplate.opsForValue().increment(attemptsKey);
      if (attempts != null && attempts == 1L) {
        redisTemplate.expire(attemptsKey, properties.challengeTtl());
      }
    } catch (DataAccessException ex) {
      log.error("Redis unavailable while counting MFA attempts, invalidating challenge", ex);
      throw MfaException.challengeStoreUnavailable();
    }
    if (attempts != null && attempts >= properties.maxChallengeAttempts()) {
      invalidate(token);
      throw MfaException.invalidChallenge();
    }
  }

  public void invalidate(String token) {
    try {
      redisTemplate.delete(challengeKey(token));
      redisTemplate.delete(attemptsKey(token));
    } catch (DataAccessException ex) {
      log.warn("Redis unavailable while invalidating MFA challenge", ex);
    }
  }

  private String generateToken() {
    byte[] buffer = new byte[TOKEN_BYTE_LENGTH];
    secureRandom.nextBytes(buffer);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
  }

  private String challengeKey(String token) {
    return CHALLENGE_KEY_PREFIX + token;
  }

  private String attemptsKey(String token) {
    return ATTEMPTS_KEY_PREFIX + token;
  }
}
