package com.pravoos.ai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class SchedulerLockConfigTest {

  private final SchedulerLockConfig config = new SchedulerLockConfig();

  @Test
  void lockProviderIsRedisBacked() {
    LockProvider lockProvider = config.lockProvider(mock(RedisConnectionFactory.class));

    assertThat(lockProvider).isInstanceOf(RedisLockProvider.class);
  }
}
