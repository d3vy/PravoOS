package com.pravoos.ai.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mailbox.sync")
public record MailSyncProperties(int maxMessagesPerRun, int maxBodyLength, int maxAutoLinkBacklog) {

  public MailSyncProperties {
    maxMessagesPerRun = maxMessagesPerRun <= 0 ? 100 : maxMessagesPerRun;
    maxBodyLength = maxBodyLength <= 0 ? 100_000 : maxBodyLength;
    maxAutoLinkBacklog = maxAutoLinkBacklog <= 0 ? 500 : maxAutoLinkBacklog;
  }
}
