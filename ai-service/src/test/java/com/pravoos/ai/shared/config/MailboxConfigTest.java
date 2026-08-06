package com.pravoos.ai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.shared.mail.ImapMailboxReader;
import com.pravoos.ai.shared.mail.MailboxReader;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MailboxConfigTest {

  private final MailboxConfig config = new MailboxConfig();

  @Test
  void imapMailboxReaderBuildsFromProperties() {
    MailboxReader reader =
        config.imapMailboxReader(
            new MailboxProperties(Duration.ofSeconds(5), Duration.ofSeconds(10), 5),
            new MailSyncProperties(100, 1000),
            new MailAttachmentProperties(10, 1024));

    assertThat(reader).isInstanceOf(ImapMailboxReader.class);
  }
}
