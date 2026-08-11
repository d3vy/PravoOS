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
public class MailboxSyncLock {

  private static final Logger log = LoggerFactory.getLogger(MailboxSyncLock.class);
  private static final String KEY_PREFIX = "mailbox:sync:";
  private static final Duration LEASE = Duration.ofMinutes(15);
  private static final Duration COOLDOWN = Duration.ofMinutes(1);

  private static final RedisScript<Long> RELEASE_IF_OWNER =
      new DefaultRedisScript<>(
          "if redis.call('get', KEYS[1]) == ARGV[1] then"
              + " return redis.call('set', KEYS[1], ARGV[1], 'EX', ARGV[2]) and 1 or 0"
              + " else return 0 end",
          Long.class);

  private final StringRedisTemplate redisTemplate;

  public MailboxSyncLock(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public String acquire(UUID mailboxId) {
    String token = UUID.randomUUID().toString();
    try {
      if (Boolean.TRUE.equals(
          redisTemplate.opsForValue().setIfAbsent(key(mailboxId), token, LEASE))) {
        return token;
      }
      return null;
    } catch (DataAccessException e) {
      log.warn("Redis недоступен при взятии лока ящика {}, синк пропущен", mailboxId, e);
      return null;
    }
  }

  public void release(UUID mailboxId, String token) {
    if (token == null) {
      return;
    }
    try {
      Long released =
          redisTemplate.execute(
              RELEASE_IF_OWNER,
              List.of(key(mailboxId)),
              token,
              String.valueOf(COOLDOWN.toSeconds()));
      if (released == null || released == 0L) {
        log.warn("Лок ящика {} истёк до конца синка и уже принадлежит другому запуску", mailboxId);
      }
    } catch (DataAccessException e) {
      log.warn("Redis недоступен при снятии лока ящика {}, лок истечёт сам", mailboxId, e);
    }
  }

  private String key(UUID mailboxId) {
    return KEY_PREFIX + mailboxId;
  }
}
