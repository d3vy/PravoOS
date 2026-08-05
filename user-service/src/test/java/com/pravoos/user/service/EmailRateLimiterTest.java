package com.pravoos.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.user.shared.config.ResendProperties;
import com.pravoos.user.shared.service.EmailRateLimiter;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class EmailRateLimiterTest {

  private static final String KEY = "email_rate:verification:user@example.com";

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private ResendProperties properties;
  private EmailRateLimiter limiter;

  @BeforeEach
  void setUp() {
    properties = new ResendProperties("key", "from@pravoos.com", "https://app", 24, 2, 5);
    limiter = new EmailRateLimiter(redisTemplate, properties);
  }

  @Test
  void allow_normalizesEmail_forKey() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(KEY)).thenReturn(1L);

    boolean allowed = limiter.allow("verification", "User@Example.com");

    assertThat(allowed).isTrue();
    verify(valueOperations).increment(KEY);
  }

  @Test
  void allow_setsWindowExpiry_onFirstRequest() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(KEY)).thenReturn(1L);

    limiter.allow("verification", "user@example.com");

    verify(redisTemplate).expire(KEY, Duration.ofHours(1));
  }

  @Test
  void allow_doesNotResetExpiry_onSubsequentRequests() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(KEY)).thenReturn(3L);

    limiter.allow("verification", "user@example.com");

    verify(redisTemplate, never()).expire(KEY, Duration.ofHours(1));
  }

  @Test
  void allow_returnsTrue_whenWithinLimit() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(KEY)).thenReturn(5L);

    assertThat(limiter.allow("verification", "user@example.com")).isTrue();
  }

  @Test
  void allow_returnsFalse_whenLimitExceeded() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(KEY)).thenReturn(6L);

    assertThat(limiter.allow("verification", "user@example.com")).isFalse();
  }

  @Test
  void allow_returnsTrue_whenIncrementReturnsNull() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(KEY)).thenReturn(null);

    assertThat(limiter.allow("verification", "user@example.com")).isTrue();
  }

  @Test
  void allow_failsOpen_whenRedisUnavailable() {
    when(redisTemplate.opsForValue()).thenThrow(new QueryTimeoutException("redis down"));

    assertThat(limiter.allow("verification", "user@example.com")).isTrue();
  }
}
