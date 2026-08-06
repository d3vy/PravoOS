package com.pravoos.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.user.identity.internal.config.BruteForceProperties;
import com.pravoos.user.identity.internal.service.LoginAttemptService;
import com.pravoos.user.shared.exception.BruteForceProtectionUnavailableException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

  private static final String EMAIL = "User@Example.com";
  private static final String ATTEMPTS_KEY = "login_attempts:user@example.com";
  private static final String LOCK_KEY = "login_lock:user@example.com";

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private BruteForceProperties properties;
  private LoginAttemptService service;

  @BeforeEach
  void setUp() {
    properties = new BruteForceProperties(5, Duration.ofMinutes(15), Duration.ofMinutes(10), true);
    service = new LoginAttemptService(redisTemplate, properties);
  }

  @Test
  void remainingLockSeconds_returnsTtl_whenLocked() {
    when(redisTemplate.getExpire(LOCK_KEY, TimeUnit.SECONDS)).thenReturn(120L);

    assertThat(service.remainingLockSeconds(EMAIL)).contains(120L);
  }

  @Test
  void remainingLockSeconds_returnsEmpty_whenNotLocked() {
    when(redisTemplate.getExpire(LOCK_KEY, TimeUnit.SECONDS)).thenReturn(-2L);

    assertThat(service.remainingLockSeconds(EMAIL)).isEmpty();
  }

  @Test
  void remainingLockSeconds_returnsEmpty_whenTtlIsNull() {
    when(redisTemplate.getExpire(LOCK_KEY, TimeUnit.SECONDS)).thenReturn(null);

    assertThat(service.remainingLockSeconds(EMAIL)).isEmpty();
  }

  @Test
  void remainingLockSeconds_failsOpen_whenRedisUnavailableAndFailOpenEnabled() {
    when(redisTemplate.getExpire(LOCK_KEY, TimeUnit.SECONDS))
        .thenThrow(new QueryTimeoutException("redis down"));

    assertThat(service.remainingLockSeconds(EMAIL)).isEmpty();
  }

  @Test
  void remainingLockSeconds_failsClosed_whenRedisUnavailableAndFailOpenDisabled() {
    properties = new BruteForceProperties(5, Duration.ofMinutes(15), Duration.ofMinutes(10), false);
    service = new LoginAttemptService(redisTemplate, properties);
    when(redisTemplate.getExpire(LOCK_KEY, TimeUnit.SECONDS))
        .thenThrow(new QueryTimeoutException("redis down"));

    assertThatThrownBy(() -> service.remainingLockSeconds(EMAIL))
        .isInstanceOf(BruteForceProtectionUnavailableException.class);
  }

  @Test
  void recordFailure_normalizesEmail_forAttemptsKey() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(ATTEMPTS_KEY)).thenReturn(1L);
    when(redisTemplate.getExpire(ATTEMPTS_KEY, TimeUnit.SECONDS)).thenReturn(-1L);

    service.recordFailure(EMAIL);

    verify(valueOperations).increment(ATTEMPTS_KEY);
  }

  @Test
  void recordFailure_setsWindowExpiry_onFirstAttempt() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(ATTEMPTS_KEY)).thenReturn(1L);

    service.recordFailure(EMAIL);

    verify(redisTemplate).expire(ATTEMPTS_KEY, Duration.ofMinutes(10));
  }

  @Test
  void recordFailure_setsWindowExpiry_whenKeyHasNoTtl() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(ATTEMPTS_KEY)).thenReturn(3L);
    when(redisTemplate.getExpire(ATTEMPTS_KEY, TimeUnit.SECONDS)).thenReturn(-1L);

    service.recordFailure(EMAIL);

    verify(redisTemplate).expire(ATTEMPTS_KEY, Duration.ofMinutes(10));
  }

  @Test
  void recordFailure_doesNotResetExpiry_whenWithinWindow() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(ATTEMPTS_KEY)).thenReturn(3L);
    when(redisTemplate.getExpire(ATTEMPTS_KEY, TimeUnit.SECONDS)).thenReturn(300L);

    service.recordFailure(EMAIL);

    verify(redisTemplate, never()).expire(eq(ATTEMPTS_KEY), any(Duration.class));
  }

  @Test
  void recordFailure_returnsEarly_whenIncrementReturnsNull() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(ATTEMPTS_KEY)).thenReturn(null);

    service.recordFailure(EMAIL);

    verify(redisTemplate, never()).getExpire(anyString(), any(TimeUnit.class));
    verify(redisTemplate, never()).delete(anyString());
  }

  @Test
  void recordFailure_locksAccount_whenMaxAttemptsReached() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(ATTEMPTS_KEY)).thenReturn(5L);
    when(redisTemplate.getExpire(ATTEMPTS_KEY, TimeUnit.SECONDS)).thenReturn(300L);

    service.recordFailure(EMAIL);

    verify(valueOperations).set(LOCK_KEY, "1", Duration.ofMinutes(15));
    verify(redisTemplate).delete(ATTEMPTS_KEY);
  }

  @Test
  void recordFailure_doesNotLock_belowMaxAttempts() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment(ATTEMPTS_KEY)).thenReturn(4L);
    when(redisTemplate.getExpire(ATTEMPTS_KEY, TimeUnit.SECONDS)).thenReturn(300L);

    service.recordFailure(EMAIL);

    verify(valueOperations, never()).set(eq(LOCK_KEY), anyString(), any(Duration.class));
    verify(redisTemplate, never()).delete(ATTEMPTS_KEY);
  }

  @Test
  void recordFailure_failsOpen_whenRedisUnavailableAndFailOpenEnabled() {
    when(redisTemplate.opsForValue()).thenThrow(new QueryTimeoutException("redis down"));

    service.recordFailure(EMAIL);
  }

  @Test
  void recordFailure_failsClosed_whenRedisUnavailableAndFailOpenDisabled() {
    properties = new BruteForceProperties(5, Duration.ofMinutes(15), Duration.ofMinutes(10), false);
    service = new LoginAttemptService(redisTemplate, properties);
    when(redisTemplate.opsForValue()).thenThrow(new QueryTimeoutException("redis down"));

    assertThatThrownBy(() -> service.recordFailure(EMAIL))
        .isInstanceOf(BruteForceProtectionUnavailableException.class);
  }

  @Test
  void reset_deletesAttemptsAndLockKeys() {
    service.reset(EMAIL);

    verify(redisTemplate).delete(ATTEMPTS_KEY);
    verify(redisTemplate).delete(LOCK_KEY);
  }

  @Test
  void reset_swallowsRedisFailure() {
    when(redisTemplate.delete(ATTEMPTS_KEY)).thenThrow(new QueryTimeoutException("redis down"));

    service.reset(EMAIL);
  }
}
