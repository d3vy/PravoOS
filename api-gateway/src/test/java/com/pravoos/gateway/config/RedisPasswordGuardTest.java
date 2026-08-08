package com.pravoos.gateway.config;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

class RedisPasswordGuardTest {

  @Test
  void verifyRedisPasswordPresentThrowsWhenPasswordBlank() {
    RedisPasswordGuard guard = new RedisPasswordGuard("  ");

    assertThatIllegalStateException()
        .isThrownBy(guard::verifyRedisPasswordPresent)
        .withMessageContaining("REDIS_PASSWORD");
  }

  @Test
  void verifyRedisPasswordPresentThrowsWhenPasswordNull() {
    RedisPasswordGuard guard = new RedisPasswordGuard(null);

    assertThatIllegalStateException().isThrownBy(guard::verifyRedisPasswordPresent);
  }

  @Test
  void verifyRedisPasswordPresentPassesWhenPasswordConfigured() {
    RedisPasswordGuard guard = new RedisPasswordGuard("strong-password");

    assertThatNoException().isThrownBy(guard::verifyRedisPasswordPresent);
  }
}
