package com.pravoos.notification.service;

import com.pravoos.notification.client.UserServiceClient;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class TelegramChatIdResolver {

  private static final Logger log = LoggerFactory.getLogger(TelegramChatIdResolver.class);
  private static final String KEY_PREFIX = "notif:chatid:";
  private static final String NONE_MARKER = "none";
  private static final Duration LINKED_TTL = Duration.ofMinutes(30);
  private static final Duration UNLINKED_TTL = Duration.ofMinutes(2);

  private final UserServiceClient userServiceClient;
  private final StringRedisTemplate redisTemplate;

  public TelegramChatIdResolver(
      UserServiceClient userServiceClient, StringRedisTemplate redisTemplate) {
    this.userServiceClient = userServiceClient;
    this.redisTemplate = redisTemplate;
  }

  public Optional<Long> resolve(UUID lawyerId) {
    String key = KEY_PREFIX + lawyerId;
    Optional<Long> cached = readCache(key);
    if (cached != null) {
      return cached;
    }
    Optional<Long> resolved = userServiceClient.resolveTelegramChatId(lawyerId);
    writeCache(key, resolved);
    return resolved;
  }

  private Optional<Long> readCache(String key) {
    try {
      String value = redisTemplate.opsForValue().get(key);
      if (value == null) {
        return null;
      }
      return NONE_MARKER.equals(value) ? Optional.empty() : Optional.of(Long.parseLong(value));
    } catch (DataAccessException | NumberFormatException ex) {
      log.warn("Failed to read cached Telegram chatId ({}), falling back to user-service", key, ex);
      return null;
    }
  }

  private void writeCache(String key, Optional<Long> resolved) {
    try {
      if (resolved.isPresent()) {
        redisTemplate.opsForValue().set(key, resolved.get().toString(), LINKED_TTL);
      } else {
        redisTemplate.opsForValue().set(key, NONE_MARKER, UNLINKED_TTL);
      }
    } catch (DataAccessException ex) {
      log.warn("Failed to cache Telegram chatId ({})", key, ex);
    }
  }
}
