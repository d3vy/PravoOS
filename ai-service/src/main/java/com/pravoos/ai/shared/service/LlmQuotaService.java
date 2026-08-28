package com.pravoos.ai.shared.service;

import com.pravoos.ai.shared.exception.LlmQuotaExceededException;
import com.pravoos.ai.shared.security.PlanLimitsProvider;
import com.pravoos.common.web.PlanLimits;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LlmQuotaService {

  private static final Logger log = LoggerFactory.getLogger(LlmQuotaService.class);
  private static final String REQUEST_KEY_PREFIX = "llm_quota:";
  private static final String TOKEN_KEY_PREFIX = "llm_tokens:";
  private static final Duration WINDOW = Duration.ofDays(1);

  private final StringRedisTemplate redisTemplate;
  private final PlanLimitsProvider planLimitsProvider;

  public LlmQuotaService(StringRedisTemplate redisTemplate, PlanLimitsProvider planLimitsProvider) {
    this.redisTemplate = redisTemplate;
    this.planLimitsProvider = planLimitsProvider;
  }

  public void assertWithinQuota(UUID lawyerId) {
    PlanLimits limits = planLimitsProvider.currentLimits();
    if (limits.quotaDisabled()) {
      return;
    }
    try {
      if (limits.dailyRequests() > 0) {
        long requests = readCounter(requestKey(lawyerId));
        if (requests >= limits.dailyRequests()) {
          log.warn(
              "LLM daily request quota exceeded for lawyer {} on plan {} ({}/{})",
              lawyerId,
              limits.code(),
              requests,
              limits.dailyRequests());
          throw new LlmQuotaExceededException();
        }
      }
      if (limits.dailyTokens() > 0) {
        long tokens = readCounter(tokenKey(lawyerId));
        if (tokens >= limits.dailyTokens()) {
          log.warn(
              "LLM daily token budget exceeded for lawyer {} on plan {} ({}/{})",
              lawyerId,
              limits.code(),
              tokens,
              limits.dailyTokens());
          throw new LlmQuotaExceededException();
        }
      }
    } catch (DataAccessException ex) {
      log.warn("Redis unavailable during LLM quota check, allowing", ex);
    }
  }

  public void assertQuotaHeadroom(UUID lawyerId, int requestedCalls) {
    if (requestedCalls <= 1) {
      assertWithinQuota(lawyerId);
      return;
    }
    PlanLimits limits = planLimitsProvider.currentLimits();
    if (limits.quotaDisabled() || limits.dailyRequests() <= 0) {
      assertWithinQuota(lawyerId);
      return;
    }
    try {
      long requests = readCounter(requestKey(lawyerId));
      if (requests + requestedCalls > limits.dailyRequests()) {
        log.warn(
            "LLM daily request quota headroom too small for lawyer {} on plan {} ({}+{}/{})",
            lawyerId,
            limits.code(),
            requests,
            requestedCalls,
            limits.dailyRequests());
        throw new LlmQuotaExceededException();
      }
    } catch (DataAccessException ex) {
      log.warn("Redis unavailable during LLM quota headroom check, allowing", ex);
    }
    assertWithinQuota(lawyerId);
  }

  public void recordUsage(UUID lawyerId, long totalTokens) {
    recordUsage(lawyerId, totalTokens, 1);
  }

  public void recordUsage(UUID lawyerId, long totalTokens, int requestCount) {
    if (planLimitsProvider.currentLimits().quotaDisabled()) {
      return;
    }
    try {
      incrementWithTtl(requestKey(lawyerId), Math.max(1L, requestCount));
      if (totalTokens > 0) {
        incrementWithTtl(tokenKey(lawyerId), totalTokens);
      }
    } catch (DataAccessException ex) {
      log.warn("Redis unavailable during LLM usage recording for lawyer {}", lawyerId, ex);
    }
  }

  public void recordTokenUsage(UUID lawyerId, long totalTokens) {
    PlanLimits limits = planLimitsProvider.currentLimits();
    if (totalTokens <= 0 || limits.quotaDisabled() || limits.dailyTokens() <= 0) {
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
    return REQUEST_KEY_PREFIX + lawyerId + ":" + LocalDate.now(ZoneOffset.UTC);
  }

  private String tokenKey(UUID lawyerId) {
    return TOKEN_KEY_PREFIX + lawyerId + ":" + LocalDate.now(ZoneOffset.UTC);
  }
}
