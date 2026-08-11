package com.pravoos.ai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MailSyncPropertiesTest {

  @Test
  void defaultsApplyWhenMaxMessagesPerRunNonPositive() {
    assertThat(new MailSyncProperties(0, 1000, 500).maxMessagesPerRun()).isEqualTo(100);
    assertThat(new MailSyncProperties(-5, 1000, 500).maxMessagesPerRun()).isEqualTo(100);
  }

  @Test
  void defaultsApplyWhenMaxBodyLengthNonPositive() {
    assertThat(new MailSyncProperties(50, 0, 500).maxBodyLength()).isEqualTo(100_000);
    assertThat(new MailSyncProperties(50, -1, 500).maxBodyLength()).isEqualTo(100_000);
  }

  @Test
  void explicitPositiveValuesArePreserved() {
    MailSyncProperties properties = new MailSyncProperties(25, 5000, 500);

    assertThat(properties.maxMessagesPerRun()).isEqualTo(25);
    assertThat(properties.maxBodyLength()).isEqualTo(5000);
  }
}
