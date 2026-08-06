package com.pravoos.ai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MailboxPropertiesTest {

  @Test
  void defaultsApplyWhenTimeoutsAreNull() {
    MailboxProperties properties = new MailboxProperties(null, null, 5);

    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(10));
    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void defaultAppliesWhenMaxPerUserNonPositive() {
    assertThat(new MailboxProperties(Duration.ofSeconds(1), Duration.ofSeconds(1), 0).maxPerUser())
        .isEqualTo(5);
    assertThat(new MailboxProperties(Duration.ofSeconds(1), Duration.ofSeconds(1), -1).maxPerUser())
        .isEqualTo(5);
  }

  @Test
  void explicitValuesArePreserved() {
    MailboxProperties properties =
        new MailboxProperties(Duration.ofSeconds(3), Duration.ofSeconds(15), 8);

    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(15));
    assertThat(properties.maxPerUser()).isEqualTo(8);
  }
}
