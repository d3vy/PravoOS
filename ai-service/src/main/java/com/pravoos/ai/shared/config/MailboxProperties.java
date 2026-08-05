package com.pravoos.ai.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mailbox.imap")
public record MailboxProperties(Duration connectTimeout, Duration readTimeout, int maxPerUser) {

  public MailboxProperties {
    connectTimeout = connectTimeout == null ? Duration.ofSeconds(10) : connectTimeout;
    readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
    maxPerUser = maxPerUser <= 0 ? 5 : maxPerUser;
  }
}
