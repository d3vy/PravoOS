package com.pravoos.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.user.identity.internal.config.MfaProperties;
import com.pravoos.user.identity.internal.service.MfaChallengeService;
import com.pravoos.user.shared.exception.MfaException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class MfaChallengeServiceTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private MfaProperties properties;
  private MfaChallengeService service;

  @BeforeEach
  void setUp() {
    properties = new MfaProperties("PravoOS", true, Duration.ofMinutes(5), 3);
    service = new MfaChallengeService(redisTemplate, properties);
  }

  @Test
  void createChallenge_storesUserId_underGeneratedToken() {
    UUID userId = UUID.randomUUID();
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    String token = service.createChallenge(userId);

    assertThat(token).isNotBlank();
    verify(valueOperations)
        .set("mfa_challenge:" + token, userId.toString(), Duration.ofMinutes(5));
  }

  @Test
  void createChallenge_generatesDifferentTokens() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    String first = service.createChallenge(UUID.randomUUID());
    String second = service.createChallenge(UUID.randomUUID());

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void resolve_returnsUserId_whenChallengeExists() {
    UUID userId = UUID.randomUUID();
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("mfa_challenge:token")).thenReturn(userId.toString());

    assertThat(service.resolve("token")).isEqualTo(userId);
  }

  @Test
  void resolve_throws_whenChallengeMissing() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("mfa_challenge:token")).thenReturn(null);

    assertThatThrownBy(() -> service.resolve("token"))
        .isInstanceOf(MfaException.class)
        .hasFieldOrPropertyWithValue("code", "MFA_INVALID_CHALLENGE");
  }

  @Test
  void registerFailedAttempt_setsExpiry_onFirstAttempt() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment("mfa_attempts:token")).thenReturn(1L);

    service.registerFailedAttempt("token");

    verify(redisTemplate).expire("mfa_attempts:token", Duration.ofMinutes(5));
  }

  @Test
  void registerFailedAttempt_doesNotSetExpiry_afterFirstAttempt() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment("mfa_attempts:token")).thenReturn(2L);

    service.registerFailedAttempt("token");

    verify(redisTemplate, org.mockito.Mockito.never())
        .expire(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void registerFailedAttempt_invalidatesChallenge_whenMaxAttemptsReached() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment("mfa_attempts:token")).thenReturn(3L);

    assertThatThrownBy(() -> service.registerFailedAttempt("token"))
        .isInstanceOf(MfaException.class)
        .hasFieldOrPropertyWithValue("code", "MFA_INVALID_CHALLENGE");

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(redisTemplate, org.mockito.Mockito.times(2)).delete(keyCaptor.capture());
    assertThat(keyCaptor.getAllValues())
        .containsExactly("mfa_challenge:token", "mfa_attempts:token");
  }

  @Test
  void registerFailedAttempt_belowLimit_doesNotInvalidate() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment("mfa_attempts:token")).thenReturn(2L);

    service.registerFailedAttempt("token");

    verify(redisTemplate, org.mockito.Mockito.never())
        .delete(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void invalidate_deletesChallengeAndAttemptsKeys() {
    service.invalidate("token");

    verify(redisTemplate).delete("mfa_challenge:token");
    verify(redisTemplate).delete("mfa_attempts:token");
  }
}
