package com.pravoos.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ProcessedEventGuard {

    private static final Logger log = LoggerFactory.getLogger(ProcessedEventGuard.class);
    private static final String KEY_PREFIX = "notif:processed:";
    private static final Duration TTL = Duration.ofDays(3);

    private final StringRedisTemplate redisTemplate;

    public ProcessedEventGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isProcessed(String eventType, String dedupKey) {
        String key = key(eventType, dedupKey);
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (DataAccessException ex) {
            log.warn("Redis unavailable for dedup check ({}), processing event to avoid loss", key, ex);
            return false;
        }
    }

    public void markProcessed(String eventType, String dedupKey) {
        String key = key(eventType, dedupKey);
        try {
            redisTemplate.opsForValue().set(key, "1", TTL);
        } catch (DataAccessException ex) {
            log.warn("Redis unavailable to mark event processed ({}), may be redelivered", key, ex);
        }
    }

    private String key(String eventType, String dedupKey) {
        return KEY_PREFIX + eventType + ":" + dedupKey;
    }
}
