package com.pravoos.notification.service;

import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProcessedEventGuard {

  private static final Logger log = LoggerFactory.getLogger(ProcessedEventGuard.class);
  private static final String KEY_PREFIX = "notif:processed:";
  private static final Duration TTL = Duration.ofDays(3);
  private static final String OUTCOME_PENDING = "pending";
  private static final String OUTCOME_REACHED = "reached";
  private static final String OUTCOME_NOT_REACHED = "not-reached";

  private final StringRedisTemplate redisTemplate;

  public ProcessedEventGuard(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public boolean claim(String eventType, String dedupKey) {
    String key = key(eventType, dedupKey);
    try {
      return Boolean.TRUE.equals(
          redisTemplate.opsForValue().setIfAbsent(key, OUTCOME_PENDING, TTL));
    } catch (DataAccessException ex) {
      log.warn("Redis unavailable for dedup claim ({}), processing event to avoid loss", key, ex);
      return true;
    }
  }

  public void recordOutcome(String eventType, String dedupKey, boolean reachedRecipient) {
    String key = key(eventType, dedupKey);
    try {
      redisTemplate
          .opsForValue()
          .set(key, reachedRecipient ? OUTCOME_REACHED : OUTCOME_NOT_REACHED, TTL);
    } catch (DataAccessException ex) {
      log.warn("Redis unavailable to record outcome for {}", key, ex);
    }
  }

  public Optional<Boolean> previousOutcome(String eventType, String dedupKey) {
    String key = key(eventType, dedupKey);
    try {
      String stored = redisTemplate.opsForValue().get(key);
      if (stored == null || OUTCOME_PENDING.equals(stored)) {
        return Optional.empty();
      }
      return Optional.of(OUTCOME_REACHED.equals(stored));
    } catch (DataAccessException ex) {
      log.warn("Redis unavailable to read outcome for {}", key, ex);
      return Optional.empty();
    }
  }

  public void release(String eventType, String dedupKey) {
    String key = key(eventType, dedupKey);
    try {
      redisTemplate.delete(key);
    } catch (DataAccessException ex) {
      log.warn("Redis unavailable to release dedup claim ({}), retry will be skipped", key, ex);
    }
  }

  private String key(String eventType, String dedupKey) {
    return KEY_PREFIX + eventType + ":" + dedupKey;
  }
}
