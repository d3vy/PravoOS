package com.pravoos.ai.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class FetchedEmailMapperTest {

  private final FetchedEmailMapper mapper = new FetchedEmailMapper(1000);

  @Test
  void prefersPlainTextPartAndCountsAttachments() throws Exception {
    String raw =
        """
        From: Ivan <IVAN@example.com>
        To: lawyer@pravoos.ru, colleague@example.com
        Cc: boss@example.com
        Subject: Contract
        Message-ID: <abc123@example.com>
        In-Reply-To: <root@example.com>
        References: <root@example.com> <second@example.com>
        Date: Mon, 5 Aug 2024 10:15:00 +0000
        MIME-Version: 1.0
        Content-Type: multipart/mixed; boundary="MIX"

        --MIX
        Content-Type: multipart/alternative; boundary="ALT"

        --ALT
        Content-Type: text/plain; charset=UTF-8

        Plain body
        --ALT
        Content-Type: text/html; charset=UTF-8

        <p>HTML body</p>
        --ALT--
        --MIX
        Content-Type: application/pdf; name="dogovor.pdf"
        Content-Disposition: attachment; filename="dogovor.pdf"

        pdf-bytes
        --MIX--
        """;

    FetchedEmail email = mapper.map(message(raw), 17L, 99L, "imap.example.com");

    assertThat(email.messageId()).isEqualTo("<abc123@example.com>");
    assertThat(email.uid()).isEqualTo(17L);
    assertThat(email.subject()).isEqualTo("Contract");
    assertThat(email.fromAddress()).isEqualTo("ivan@example.com");
    assertThat(email.toAddresses()).containsExactly("lawyer@pravoos.ru", "colleague@example.com");
    assertThat(email.ccAddresses()).containsExactly("boss@example.com");
    assertThat(email.bodyText()).isEqualTo("Plain body");
    assertThat(email.attachmentCount()).isEqualTo(1);
    assertThat(email.hasAttachments()).isTrue();
    assertThat(email.inReplyTo()).isEqualTo("<root@example.com>");
    assertThat(email.references()).containsExactly("<root@example.com>", "<second@example.com>");
    assertThat(email.sentAt()).isEqualTo(LocalDateTime.of(2024, 8, 5, 10, 15));
  }

  @Test
  void convertsHtmlOnlyBodyToText() throws Exception {
    String raw =
        """
        From: client@example.com
        To: lawyer@pravoos.ru
        Subject: Only html
        Message-ID: <html-1@example.com>
        MIME-Version: 1.0
        Content-Type: text/html; charset=UTF-8

        <div><p>Первый абзац</p><p>Второй абзац</p></div>
        """;

    FetchedEmail email = mapper.map(message(raw), 5L, 99L, "imap.example.com");

    assertThat(email.bodyText()).isEqualTo("Первый абзац\n\nВторой абзац");
    assertThat(email.attachmentCount()).isZero();
  }

  @Test
  void fallsBackToSyntheticMessageIdWhenHeaderMissing() throws Exception {
    String raw =
        """
        From: client@example.com
        To: lawyer@pravoos.ru
        Subject: No id
        Content-Type: text/plain; charset=UTF-8

        Body
        """;

    FetchedEmail email = mapper.map(message(raw), 8L, 99L, "imap.example.com");

    assertThat(email.messageId()).isEqualTo("<uid-99-8@imap.example.com>");
  }

  @Test
  void truncatesBodyToConfiguredLimit() throws Exception {
    String raw =
        """
        From: client@example.com
        To: lawyer@pravoos.ru
        Subject: Long
        Message-ID: <long-1@example.com>
        Content-Type: text/plain; charset=UTF-8

        %s
        """
            .formatted("a".repeat(5000));

    FetchedEmail email = new FetchedEmailMapper(100).map(message(raw), 9L, 99L, "imap.example.com");

    assertThat(email.bodyText()).hasSize(100);
  }

  private MimeMessage message(String raw) throws Exception {
    return new MimeMessage(
        Session.getInstance(new Properties()),
        new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
  }
}
