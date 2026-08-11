package com.pravoos.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class ProcessedEventGuardTest {

  private static final String KEY = "notif:processed:invoice.paid:123";

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private ProcessedEventGuard guard;

  @BeforeEach
  void setUp() {
    guard = new ProcessedEventGuard(redisTemplate);
  }

  @Test
  void claimSucceedsForFirstCallerAndSetsThreeDayTtl() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(KEY, "pending", Duration.ofDays(3))).thenReturn(true);

    assertThat(guard.claim("invoice.paid", "123")).isTrue();
    verify(valueOperations).setIfAbsent(eq(KEY), eq("pending"), eq(Duration.ofDays(3)));
  }

  @Test
  void claimFailsWhenAnotherConsumerAlreadyHoldsTheKey() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(KEY, "pending", Duration.ofDays(3))).thenReturn(false);

    assertThat(guard.claim("invoice.paid", "123")).isFalse();
  }

  @Test
  void previousOutcomeReportsWhetherTheChannelActuallyReachedTheRecipient() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(KEY)).thenReturn("reached", "not-reached", "pending");

    assertThat(guard.previousOutcome("invoice.paid", "123")).contains(true);
    assertThat(guard.previousOutcome("invoice.paid", "123")).contains(false);
    assertThat(guard.previousOutcome("invoice.paid", "123")).isEmpty();
  }

  @Test
  void recordOutcomeOverwritesThePendingMarker() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    guard.recordOutcome("invoice.paid", "123", true);

    verify(valueOperations).set(KEY, "reached", Duration.ofDays(3));
  }

  @Test
  void claimFailsOpenOnRedisErrorSoEventsAreNotLost() {
    when(redisTemplate.opsForValue()).thenThrow(new QueryTimeoutException("timeout"));

    assertThat(guard.claim("invoice.paid", "123")).isTrue();
  }

  @Test
  void releaseDeletesTheKeySoARetryCanReprocess() {
    guard.release("invoice.paid", "123");

    verify(redisTemplate).delete(KEY);
  }

  @Test
  void releaseSwallowsRedisError() {
    when(redisTemplate.delete(any(String.class))).thenThrow(new QueryTimeoutException("timeout"));

    guard.release("invoice.paid", "123");
  }
}
