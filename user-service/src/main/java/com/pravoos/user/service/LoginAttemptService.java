package com.pravoos.user.service;

import com.pravoos.user.config.BruteForceProperties;
import com.pravoos.user.util.EmailNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
    private static final String ATTEMPTS_KEY_PREFIX = "login_attempts:";
    private static final String LOCK_KEY_PREFIX = "login_lock:";

    private final StringRedisTemplate redisTemplate;
    private final BruteForceProperties properties;

    public LoginAttemptService(StringRedisTemplate redisTemplate, BruteForceProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public Optional<Long> remainingLockSeconds(String email) {
        try {
            Long ttl = redisTemplate.getExpire(lockKey(email), TimeUnit.SECONDS);
            return ttl != null && ttl > 0 ? Optional.of(ttl) : Optional.empty();
        } catch (DataAccessException ex) {
            log.warn("Redis unavailable during lock check, failing open", ex);
            return Optional.empty();
        }
    }

    public void recordFailure(String email) {
        String attemptsKey = attemptsKey(email);
        try {
            Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
            if (attempts == null) {
                return;
            }
            if (attempts == 1L) {
                redisTemplate.expire(attemptsKey, properties.attemptWindow());
            }
            if (attempts >= properties.maxAttempts()) {
                redisTemplate.opsForValue().set(lockKey(email), "1", properties.lockoutDuration());
                redisTemplate.delete(attemptsKey);
                log.warn("Account locked after {} failed login attempts: {}", attempts, email);
            }
        } catch (DataAccessException ex) {
            log.warn("Redis unavailable while recording failed login, failing open", ex);
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

    private String attemptsKey(String email) {
        return ATTEMPTS_KEY_PREFIX + EmailNormalizer.normalize(email);
    }

    private String lockKey(String email) {
        return LOCK_KEY_PREFIX + EmailNormalizer.normalize(email);
    }
}
