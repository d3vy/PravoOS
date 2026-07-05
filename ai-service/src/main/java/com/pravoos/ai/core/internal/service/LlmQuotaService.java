package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.shared.exception.LlmQuotaExceededException;
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
    private static final String REQUEST_KEY_PREFIX = "llm_quota:";
    private static final String TOKEN_KEY_PREFIX = "llm_tokens:";
    private static final Duration WINDOW = Duration.ofDays(1);

    private final StringRedisTemplate redisTemplate;
    private final int dailyRequestLimit;
    private final long dailyTokenLimit;

    public LlmQuotaService(StringRedisTemplate redisTemplate,
                           @Value("${llm.quota.daily-requests:200}") int dailyRequestLimit,
                           @Value("${llm.quota.daily-tokens:0}") long dailyTokenLimit) {
        this.redisTemplate = redisTemplate;
        this.dailyRequestLimit = dailyRequestLimit;
        this.dailyTokenLimit = dailyTokenLimit;
    }

    public void assertWithinQuota(UUID lawyerId) {
        if (dailyRequestLimit <= 0 && dailyTokenLimit <= 0) {
            return;
        }
        try {
            if (dailyRequestLimit > 0) {
                long requests = readCounter(requestKey(lawyerId));
                if (requests >= dailyRequestLimit) {
                    log.warn("LLM daily request quota exceeded for lawyer {} ({}/{})",
                            lawyerId, requests, dailyRequestLimit);
                    throw new LlmQuotaExceededException();
                }
            }
            if (dailyTokenLimit > 0) {
                long tokens = readCounter(tokenKey(lawyerId));
                if (tokens >= dailyTokenLimit) {
                    log.warn("LLM daily token budget exceeded for lawyer {} ({}/{})",
                            lawyerId, tokens, dailyTokenLimit);
                    throw new LlmQuotaExceededException();
                }
            }
        } catch (DataAccessException ex) {
            log.warn("Redis unavailable during LLM quota check, allowing", ex);
        }
    }

    public void recordUsage(UUID lawyerId, long totalTokens) {
        if (dailyRequestLimit <= 0 && dailyTokenLimit <= 0) {
            return;
        }
        try {
            incrementWithTtl(requestKey(lawyerId), 1L);
            if (totalTokens > 0) {
                incrementWithTtl(tokenKey(lawyerId), totalTokens);
            }
        } catch (DataAccessException ex) {
            log.warn("Redis unavailable during LLM usage recording for lawyer {}", lawyerId, ex);
        }
    }

    public void recordTokenUsage(UUID lawyerId, long totalTokens) {
        if (dailyTokenLimit <= 0 || totalTokens <= 0) {
            return;
        }
        try {
            incrementWithTtl(tokenKey(lawyerId), totalTokens);
        } catch (DataAccessException ex) {
            log.warn("Redis unavailable during embedding token accounting for lawyer {}", lawyerId, ex);
        }
    }

    private void incrementWithTtl(String key, long delta) {
        Long value = redisTemplate.opsForValue().increment(key, delta);
        if (value != null && value == delta) {
            redisTemplate.expire(key, WINDOW);
        }
    }

    private long readCounter(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String requestKey(UUID lawyerId) {
        return REQUEST_KEY_PREFIX + lawyerId + ":" + LocalDate.now();
    }

    private String tokenKey(UUID lawyerId) {
        return TOKEN_KEY_PREFIX + lawyerId + ":" + LocalDate.now();
    }
}
