package com.pravoos.ai.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mailbox.attachments")
public record MailAttachmentProperties(int maxPerMessage, long maxBytes) {

  public MailAttachmentProperties {
    maxPerMessage = maxPerMessage <= 0 ? 10 : maxPerMessage;
    maxBytes = maxBytes <= 0 ? 26_214_400L : maxBytes;
  }
}
