package com.pravoos.ai.practice.internal.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class EmailAttachmentImportLock {

  private static final Logger log = LoggerFactory.getLogger(EmailAttachmentImportLock.class);
  private static final String KEY_PREFIX = "email:attachments:import:";
  private static final Duration LEASE = Duration.ofMinutes(10);

  private static final RedisScript<Long> DELETE_IF_OWNER =
      new DefaultRedisScript<>(
          "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1])"
              + " else return 0 end",
          Long.class);

  private final StringRedisTemplate redisTemplate;

  public EmailAttachmentImportLock(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public String acquire(UUID emailId) {
    String token = UUID.randomUUID().toString();
    try {
      if (Boolean.TRUE.equals(
          redisTemplate.opsForValue().setIfAbsent(key(emailId), token, LEASE))) {
        return token;
      }
      return null;
    } catch (DataAccessException e) {
      log.warn("Redis недоступен при взятии лока импорта вложений письма {}", emailId, e);
      return null;
    }
  }

  public void release(UUID emailId, String token) {
    if (token == null) {
      return;
    }
    try {
      redisTemplate.execute(DELETE_IF_OWNER, List.of(key(emailId)), token);
    } catch (DataAccessException e) {
      log.warn(
          "Redis недоступен при снятии лока импорта вложений письма {}, лок истечёт сам",
          emailId,
          e);
    }
  }

  private String key(UUID emailId) {
    return KEY_PREFIX + emailId;
  }
}
