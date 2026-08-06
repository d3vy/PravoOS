package com.pravoos.ai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MailAttachmentPropertiesTest {

  @Test
  void defaultsApplyWhenMaxPerMessageNonPositive() {
    assertThat(new MailAttachmentProperties(0, 1024).maxPerMessage()).isEqualTo(10);
    assertThat(new MailAttachmentProperties(-1, 1024).maxPerMessage()).isEqualTo(10);
  }

  @Test
  void defaultsApplyWhenMaxBytesNonPositive() {
    assertThat(new MailAttachmentProperties(5, 0).maxBytes()).isEqualTo(26_214_400L);
    assertThat(new MailAttachmentProperties(5, -1).maxBytes()).isEqualTo(26_214_400L);
  }

  @Test
  void explicitPositiveValuesArePreserved() {
    MailAttachmentProperties properties = new MailAttachmentProperties(3, 2048L);

    assertThat(properties.maxPerMessage()).isEqualTo(3);
    assertThat(properties.maxBytes()).isEqualTo(2048L);
  }
}
