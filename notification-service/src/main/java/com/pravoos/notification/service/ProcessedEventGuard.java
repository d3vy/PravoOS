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

    public boolean isFirstProcessing(String eventType, String dedupKey) {
        String key = KEY_PREFIX + eventType + ":" + dedupKey;
        try {
            Boolean set = redisTemplate.opsForValue().setIfAbsent(key, "1", TTL);
            return Boolean.TRUE.equals(set);
        } catch (DataAccessException ex) {
            log.warn("Redis unavailable for dedup check ({}), processing event to avoid loss", key, ex);
            return true;
        }
    }
}
