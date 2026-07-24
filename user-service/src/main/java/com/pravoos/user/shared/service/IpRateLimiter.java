package com.pravoos.user.shared.service;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class IpRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(IpRateLimiter.class);
  private static final String KEY_PREFIX = "ip_rate:";

  private final StringRedisTemplate redisTemplate;

  public IpRateLimiter(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public boolean allow(String purpose, String clientIp, int maxRequests, Duration window) {
    String key = KEY_PREFIX + purpose + ":" + clientIp;
    try {
      Long count = redisTemplate.opsForValue().increment(key);
      if (count == null) {
        return true;
      }
      if (count == 1L) {
        redisTemplate.expire(key, window);
      }
      if (count > maxRequests) {
        log.warn("IP rate limit exceeded for purpose '{}' from {}", purpose, clientIp);
        return false;
      }
      return true;
    } catch (DataAccessException ex) {
      log.warn("Redis unavailable during IP rate-limit check, allowing", ex);
      return true;
    }
  }
}
