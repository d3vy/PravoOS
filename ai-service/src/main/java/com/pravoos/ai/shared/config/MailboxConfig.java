package com.pravoos.ai.shared.config;

import com.pravoos.ai.shared.mail.ImapMailboxReader;
import com.pravoos.ai.shared.mail.MailHostGuard;
import com.pravoos.ai.shared.mail.MailboxReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MailboxConfig {

  @Bean
  public MailboxReader imapMailboxReader(
      MailboxProperties properties,
      MailHostGuard hostGuard,
      MailSyncProperties syncProperties,
      MailAttachmentProperties attachmentProperties) {
    return new ImapMailboxReader(properties, hostGuard, syncProperties, attachmentProperties);
  }
}
