package com.pravoos.ai.document.internal.service;

import com.pravoos.ai.shared.config.DocumentProperties;
import com.pravoos.ai.shared.exception.UploadRateLimitExceededException;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class UploadRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(UploadRateLimiter.class);
  private static final String KEY_PREFIX = "upload_rate:";
  private static final Duration WINDOW = Duration.ofMinutes(1);

  private final StringRedisTemplate redisTemplate;
  private final int maxUploadsPerMinute;

  public UploadRateLimiter(
      StringRedisTemplate redisTemplate, DocumentProperties documentProperties) {
    this.redisTemplate = redisTemplate;
    this.maxUploadsPerMinute = documentProperties.uploadRatePerMinute();
  }

  public void assertWithinLimit(UUID userId) {
    if (maxUploadsPerMinute <= 0) {
      return;
    }
    String key = KEY_PREFIX + userId;
    try {
      Long count = redisTemplate.opsForValue().increment(key);
      if (count == null) {
        return;
      }
      if (count == 1L) {
        redisTemplate.expire(key, WINDOW);
      }
      if (count > maxUploadsPerMinute) {
        log.warn(
            "Upload rate limit exceeded for user {} ({}/{} per minute)",
            userId,
            count,
            maxUploadsPerMinute);
        throw new UploadRateLimitExceededException();
      }
    } catch (DataAccessException ex) {
      log.warn("Redis unavailable during upload rate-limit check, allowing", ex);
    }
  }
}
