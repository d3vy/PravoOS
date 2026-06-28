package com.pravoos.ai.service;

import com.pravoos.ai.exception.LlmQuotaExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class LlmQuotaService {

    private static final Logger log = LoggerFactory.getLogger(LlmQuotaService.class);
    private static final String KEY_PREFIX = "llm_quota:";
    private static final Duration WINDOW = Duration.ofDays(1);

    private final StringRedisTemplate redisTemplate;
    private final int dailyLimit;

    public LlmQuotaService(StringRedisTemplate redisTemplate,
                           @Value("${llm.quota.daily-requests:200}") int dailyLimit) {
        this.redisTemplate = redisTemplate;
        this.dailyLimit = dailyLimit;
    }

    public void assertWithinQuota(UUID lawyerId) {
        if (dailyLimit <= 0) {
            return;
        }
        String key = KEY_PREFIX + lawyerId + ":" + LocalDate.now();
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                return;
            }
            if (count == 1L) {
                redisTemplate.expire(key, WINDOW);
            }
            if (count > dailyLimit) {
                log.warn("LLM daily quota exceeded for lawyer {} ({}/{})", lawyerId, count, dailyLimit);
                throw new LlmQuotaExceededException();
            }
        } catch (DataAccessException ex) {
            log.warn("Redis unavailable during LLM quota check, allowing", ex);
        }
    }
}
