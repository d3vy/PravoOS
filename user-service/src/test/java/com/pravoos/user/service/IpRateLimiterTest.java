package com.pravoos.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.user.shared.service.IpRateLimiter;
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
class IpRateLimiterTest {

  private static final String KEY = "ip_rate:login:1.2.3.4";

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private IpRateLimiter limiter;

  @BeforeEach
  void setUp() {
    limiter = new IpRateLimiter(redisTemplate);
  }

  @Test
  void allow_returnsTrue_andSetsExpiry_onFirstRequest() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(KEY)).thenReturn(1L);

    boolean allowed = limiter.allow("login", "1.2.3.4", 10, Duration.ofMinutes(1));

    assertThat(allowed).isTrue();
    verify(redisTemplate).expire(KEY, Duration.ofMinutes(1));
  }

  @Test
  void allow_doesNotResetExpiry_onSubsequentRequests() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(KEY)).thenReturn(5L);

    limiter.allow("login", "1.2.3.4", 10, Duration.ofMinutes(1));

    verify(redisTemplate, never()).expire(KEY, Duration.ofMinutes(1));
  }

  @Test
  void allow_returnsTrue_whenWithinLimit() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(KEY)).thenReturn(10L);

    assertThat(limiter.allow("login", "1.2.3.4", 10, Duration.ofMinutes(1))).isTrue();
  }

  @Test
  void allow_returnsFalse_whenLimitExceeded() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(KEY)).thenReturn(11L);

    assertThat(limiter.allow("login", "1.2.3.4", 10, Duration.ofMinutes(1))).isFalse();
  }

  @Test
  void allow_returnsTrue_whenIncrementReturnsNull() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(KEY)).thenReturn(null);

    assertThat(limiter.allow("login", "1.2.3.4", 10, Duration.ofMinutes(1))).isTrue();
  }

  @Test
  void allow_failsOpen_whenRedisUnavailable() {
    when(redisTemplate.opsForValue()).thenThrow(new QueryTimeoutException("redis down"));

    assertThat(limiter.allow("login", "1.2.3.4", 10, Duration.ofMinutes(1))).isTrue();
  }
}
