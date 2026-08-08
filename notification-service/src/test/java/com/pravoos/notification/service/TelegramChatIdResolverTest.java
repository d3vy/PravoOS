package com.pravoos.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.notification.client.UserServiceClient;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class TelegramChatIdResolverTest {

  private static final UUID LAWYER_ID = UUID.randomUUID();
  private static final String KEY = "notif:chatid:" + LAWYER_ID;

  @Mock private UserServiceClient userServiceClient;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private TelegramChatIdResolver resolver;

  private void setUp() {
    resolver = new TelegramChatIdResolver(userServiceClient, redisTemplate);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @Test
  void resolve_returnsCachedLinkedChatId_withoutCallingUserService() {
    setUp();
    when(valueOperations.get(KEY)).thenReturn("555");

    Optional<Long> result = resolver.resolve(LAWYER_ID);

    assertThat(result).contains(555L);
    verify(userServiceClient, never()).resolveTelegramChatId(any());
  }

  @Test
  void resolve_returnsCachedUnlinkedMarker_withoutCallingUserService() {
    setUp();
    when(valueOperations.get(KEY)).thenReturn("none");

    Optional<Long> result = resolver.resolve(LAWYER_ID);

    assertThat(result).isEmpty();
    verify(userServiceClient, never()).resolveTelegramChatId(any());
  }

  @Test
  void resolve_cacheMiss_resolvesViaUserServiceAndCachesLinkedWithLongTtl() {
    setUp();
    when(valueOperations.get(KEY)).thenReturn(null);
    when(userServiceClient.resolveTelegramChatId(LAWYER_ID)).thenReturn(Optional.of(777L));

    Optional<Long> result = resolver.resolve(LAWYER_ID);

    assertThat(result).contains(777L);
    verify(valueOperations).set(eq(KEY), eq("777"), eq(Duration.ofMinutes(30)));
  }

  @Test
  void resolve_cacheMiss_unresolvedCachesNoneMarkerWithShortTtl() {
    setUp();
    when(valueOperations.get(KEY)).thenReturn(null);
    when(userServiceClient.resolveTelegramChatId(LAWYER_ID)).thenReturn(Optional.empty());

    Optional<Long> result = resolver.resolve(LAWYER_ID);

    assertThat(result).isEmpty();
    verify(valueOperations).set(eq(KEY), eq("none"), eq(Duration.ofMinutes(2)));
  }

  @Test
  void resolve_corruptedCacheValue_fallsBackToUserService() {
    setUp();
    when(valueOperations.get(KEY)).thenReturn("not-a-number");
    when(userServiceClient.resolveTelegramChatId(LAWYER_ID)).thenReturn(Optional.of(1L));

    Optional<Long> result = resolver.resolve(LAWYER_ID);

    assertThat(result).contains(1L);
  }

  @Test
  void resolve_redisReadError_fallsBackToUserService() {
    resolver = new TelegramChatIdResolver(userServiceClient, redisTemplate);
    when(redisTemplate.opsForValue()).thenThrow(new QueryTimeoutException("timeout"));
    when(userServiceClient.resolveTelegramChatId(LAWYER_ID)).thenReturn(Optional.of(9L));

    Optional<Long> result = resolver.resolve(LAWYER_ID);

    assertThat(result).contains(9L);
  }
}
