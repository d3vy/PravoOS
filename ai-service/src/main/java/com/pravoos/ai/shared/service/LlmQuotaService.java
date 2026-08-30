package com.pravoos.ai.shared.service;

import com.pravoos.ai.shared.exception.LlmQuotaExceededException;
import com.pravoos.ai.shared.security.CurrentTenantProvider;
import com.pravoos.ai.shared.security.PlanLimitsProvider;
import com.pravoos.common.web.PlanLimits;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LlmQuotaService {

  private static final Logger log = LoggerFactory.getLogger(LlmQuotaService.class);
  private static final String REQUEST_KEY_PREFIX = "llm_quota:";
  private static final String TOKEN_KEY_PREFIX = "llm_tokens:";
  private static final String ORG_REQUEST_KEY_PREFIX = "llm_quota_org:";
  private static final String ORG_TOKEN_KEY_PREFIX = "llm_tokens_org:";
  private static final Duration WINDOW = Duration.ofDays(1);

  private final StringRedisTemplate redisTemplate;
  private final PlanLimitsProvider planLimitsProvider;
  private final CurrentTenantProvider currentTenantProvider;
  private final int orgQuotaMultiplier;

  public LlmQuotaService(
      StringRedisTemplate redisTemplate,
      PlanLimitsProvider planLimitsProvider,
      CurrentTenantProvider currentTenantProvider,
      @Value("${llm.quota.org-multiplier:10}") int orgQuotaMultiplier) {
    this.redisTemplate = redisTemplate;
    this.planLimitsProvider = planLimitsProvider;
    this.currentTenantProvider = currentTenantProvider;
    this.orgQuotaMultiplier = Math.max(orgQuotaMultiplier, 0);
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
      assertWithinOrgQuota(limits, 0);
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
      assertWithinOrgQuota(limits, requestedCalls);
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
    Optional<UUID> orgId = quotaScopedOrgId();
    try {
      long requests = Math.max(1L, requestCount);
      incrementWithTtl(requestKey(lawyerId), requests);
      orgId.ifPresent(org -> incrementWithTtl(orgRequestKey(org), requests));
      if (totalTokens > 0) {
        incrementWithTtl(tokenKey(lawyerId), totalTokens);
        orgId.ifPresent(org -> incrementWithTtl(orgTokenKey(org), totalTokens));
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
      quotaScopedOrgId().ifPresent(org -> incrementWithTtl(orgTokenKey(org), totalTokens));
    } catch (DataAccessException ex) {
      log.warn("Redis unavailable during embedding token accounting for lawyer {}", lawyerId, ex);
    }
  }

  private void assertWithinOrgQuota(PlanLimits limits, int requestedCalls) {
    Optional<UUID> scopedOrgId = quotaScopedOrgId();
    if (scopedOrgId.isEmpty()) {
      return;
    }
    UUID orgId = scopedOrgId.get();
    long requestLimit = (long) limits.dailyRequests() * orgQuotaMultiplier;
    if (requestLimit > 0) {
      long requests = readCounter(orgRequestKey(orgId));
      if (requests + Math.max(requestedCalls, 0) >= requestLimit) {
        log.warn(
            "LLM daily request quota exceeded for org {} on plan {} ({}+{}/{})",
            orgId,
            limits.code(),
            requests,
            requestedCalls,
            requestLimit);
        throw new LlmQuotaExceededException(LlmQuotaExceededException.Scope.ORG);
      }
    }
    long tokenLimit = limits.dailyTokens() * orgQuotaMultiplier;
    if (tokenLimit > 0) {
      long tokens = readCounter(orgTokenKey(orgId));
      if (tokens >= tokenLimit) {
        log.warn(
            "LLM daily token budget exceeded for org {} on plan {} ({}/{})",
            orgId,
            limits.code(),
            tokens,
            tokenLimit);
        throw new LlmQuotaExceededException(LlmQuotaExceededException.Scope.ORG);
      }
    }
  }

  private Optional<UUID> quotaScopedOrgId() {
    return orgQuotaMultiplier == 0 ? Optional.empty() : currentTenantProvider.currentOrgId();
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
    return REQUEST_KEY_PREFIX + lawyerId + ":" + today();
  }

  private String tokenKey(UUID lawyerId) {
    return TOKEN_KEY_PREFIX + lawyerId + ":" + today();
  }

  private String orgRequestKey(UUID orgId) {
    return ORG_REQUEST_KEY_PREFIX + orgId + ":" + today();
  }

  private String orgTokenKey(UUID orgId) {
    return ORG_TOKEN_KEY_PREFIX + orgId + ":" + today();
  }

  private LocalDate today() {
    return LocalDate.now(ZoneOffset.UTC);
  }
}
