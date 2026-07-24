package com.pravoos.user.shared.service;

import com.pravoos.user.shared.config.ResendProperties;
import com.pravoos.user.shared.util.EmailMasker;
import com.pravoos.user.shared.util.EmailNormalizer;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class EmailRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(EmailRateLimiter.class);
  private static final String KEY_PREFIX = "email_rate:";
  private static final Duration WINDOW = Duration.ofHours(1);

  private final StringRedisTemplate redisTemplate;
  private final ResendProperties resendProperties;

  public EmailRateLimiter(StringRedisTemplate redisTemplate, ResendProperties resendProperties) {
    this.redisTemplate = redisTemplate;
    this.resendProperties = resendProperties;
  }

  public boolean allow(String purpose, String rawEmail) {
    String key = KEY_PREFIX + purpose + ":" + EmailNormalizer.normalize(rawEmail);
    try {
      Long count = redisTemplate.opsForValue().increment(key);
      if (count == null) {
        return true;
      }
      if (count == 1L) {
        redisTemplate.expire(key, WINDOW);
      }
      if (count > resendProperties.maxEmailsPerHour()) {
        log.warn(
            "Email rate limit exceeded for purpose '{}': {}", purpose, EmailMasker.mask(rawEmail));
        return false;
      }
      return true;
    } catch (DataAccessException ex) {
      log.warn("Redis unavailable during email rate-limit check, allowing", ex);
      return true;
    }
  }
}
