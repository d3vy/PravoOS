package com.pravoos.user.identity.internal.service;

import com.pravoos.user.identity.internal.config.BruteForceProperties;
import com.pravoos.user.shared.exception.BruteForceProtectionUnavailableException;
import com.pravoos.user.shared.util.EmailMasker;
import com.pravoos.user.shared.util.EmailNormalizer;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

  private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
  private static final String ATTEMPTS_KEY_PREFIX = "login_attempts:";
  private static final String LOCK_KEY_PREFIX = "login_lock:";
  private static final String IP_ATTEMPTS_KEY_PREFIX = "login_attempts_ip:";
  private static final String IP_LOCK_KEY_PREFIX = "login_lock_ip:";

  private final StringRedisTemplate redisTemplate;
  private final BruteForceProperties properties;

  public LoginAttemptService(StringRedisTemplate redisTemplate, BruteForceProperties properties) {
    this.redisTemplate = redisTemplate;
    this.properties = properties;
  }

  public Optional<Long> remainingLockSeconds(String email) {
    return remainingLockSeconds(email, null);
  }

  public Optional<Long> remainingLockSeconds(String email, String ipAddress) {
    try {
      Optional<Long> emailLock = ttlSeconds(lockKey(email));
      Optional<Long> ipLock =
          ipAddress == null ? Optional.empty() : ttlSeconds(ipLockKey(ipAddress));
      return emailLock
          .map(emailSeconds -> Optional.of(Math.max(emailSeconds, ipLock.orElse(0L))))
          .orElse(ipLock);
    } catch (DataAccessException ex) {
      return onRedisFailure("lock check", ex);
    }
  }

  public void recordFailure(String email) {
    recordFailure(email, null);
  }

  public void recordFailure(String email, String ipAddress) {
    try {
      registerFailure(
          attemptsKey(email),
          lockKey(email),
          properties.maxAttempts(),
          "Account locked after {} failed login attempts: {}",
          EmailMasker.mask(email));
      if (ipAddress != null) {
        registerFailure(
            ipAttemptsKey(ipAddress),
            ipLockKey(ipAddress),
            properties.maxAttemptsPerIp(),
            "Source address locked after {} failed login attempts: {}",
            ipAddress);
      }
    } catch (DataAccessException ex) {
      onRedisFailure("record failure", ex);
    }
  }

  public void reset(String email) {
    try {
      redisTemplate.delete(attemptsKey(email));
      redisTemplate.delete(lockKey(email));
    } catch (DataAccessException ex) {
      log.warn("Redis unavailable while clearing login attempts", ex);
    }
  }

  private void registerFailure(
      String attemptsKey, String lockKey, int maxAttempts, String lockMessage, String subject) {
    Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
    if (attempts == null) {
      return;
    }
    Long ttl = redisTemplate.getExpire(attemptsKey, TimeUnit.SECONDS);
    if (attempts == 1L || ttl == null || ttl < 0) {
      redisTemplate.expire(attemptsKey, properties.attemptWindow());
    }
    if (attempts >= maxAttempts) {
      redisTemplate.opsForValue().set(lockKey, "1", properties.lockoutDuration());
      redisTemplate.delete(attemptsKey);
      log.warn(lockMessage, attempts, subject);
    }
  }

  private Optional<Long> ttlSeconds(String key) {
    Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
    return ttl != null && ttl > 0 ? Optional.of(ttl) : Optional.empty();
  }

  private Optional<Long> onRedisFailure(String operation, DataAccessException ex) {
    if (properties.failOpen()) {
      log.warn("Redis unavailable during {}, failing open", operation, ex);
      return Optional.empty();
    }
    log.error("Redis unavailable during {}, failing closed", operation, ex);
    throw new BruteForceProtectionUnavailableException();
  }

  private String attemptsKey(String email) {
    return ATTEMPTS_KEY_PREFIX + EmailNormalizer.normalize(email);
  }

  private String lockKey(String email) {
    return LOCK_KEY_PREFIX + EmailNormalizer.normalize(email);
  }

  private String ipAttemptsKey(String ipAddress) {
    return IP_ATTEMPTS_KEY_PREFIX + ipAddress;
  }

  private String ipLockKey(String ipAddress) {
    return IP_LOCK_KEY_PREFIX + ipAddress;
  }
}
