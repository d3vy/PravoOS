package com.pravoos.ai.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class AttachmentExtractorTest {

  private static final long MAX_BYTES = 1024;

  private final AttachmentExtractor extractor = new AttachmentExtractor(2, MAX_BYTES);

  @Test
  void extractsAttachmentContentAndSkipsBodyParts() throws Exception {
    MimeMessage message =
        mimeMessage(
            """
            From: client@example.com
            Subject: Договор
            Content-Type: multipart/mixed; boundary="b1"

            --b1
            Content-Type: text/plain; charset=UTF-8

            Во вложении договор.
            --b1
            Content-Type: application/pdf
            Content-Disposition: attachment; filename="contract.pdf"

            %PDF-1.4 fake
            --b1--
            """);

    List<FetchedAttachment> attachments = extractor.extract(message);

    assertThat(attachments).hasSize(1);
    FetchedAttachment attachment = attachments.getFirst();
    assertThat(attachment.fileName()).isEqualTo("contract.pdf");
    assertThat(attachment.contentType()).isEqualTo("application/pdf");
    assertThat(attachment.isSkipped()).isFalse();
    assertThat(new String(attachment.content(), StandardCharsets.UTF_8)).contains("%PDF-1.4 fake");
  }

  @Test
  void skipsAttachmentLargerThanLimitWithoutContent() throws Exception {
    MimeMessage message = messageWithAttachment("big.pdf", "x".repeat((int) MAX_BYTES + 100));

    FetchedAttachment attachment = extractor.extract(message).getFirst();

    assertThat(attachment.skipReason()).isEqualTo(AttachmentSkipReason.TOO_LARGE);
    assertThat(attachment.content()).isNull();
  }

  @Test
  void skipsAttachmentsBeyondPerMessageLimit() throws Exception {
    MimeMessage message =
        mimeMessage(
            """
            Content-Type: multipart/mixed; boundary="b1"

            --b1
            Content-Type: text/plain
            Content-Disposition: attachment; filename="one.txt"

            one
            --b1
            Content-Type: text/plain
            Content-Disposition: attachment; filename="two.txt"

            two
            --b1
            Content-Type: text/plain
            Content-Disposition: attachment; filename="three.txt"

            three
            --b1--
            """);

    List<FetchedAttachment> attachments = extractor.extract(message);

    assertThat(attachments).hasSize(3);
    assertThat(attachments.get(2).skipReason()).isEqualTo(AttachmentSkipReason.LIMIT_EXCEEDED);
    assertThat(attachments.get(2).content()).isNull();
    assertThat(attachments.get(0).content()).isNotNull();
  }

  @Test
  void stripsDirectoriesFromAttachmentName() throws Exception {
    MimeMessage message = messageWithAttachment("../../etc/passwd.txt", "root");

    assertThat(extractor.extract(message).getFirst().fileName()).isEqualTo("passwd.txt");
  }

  @Test
  void returnsEmptyListForMessageWithoutAttachments() throws Exception {
    MimeMessage message =
        mimeMessage(
            """
            Content-Type: text/plain; charset=UTF-8

            Просто письмо без вложений.
            """);

    assertThat(extractor.extract(message)).isEmpty();
  }

  private MimeMessage messageWithAttachment(String fileName, String content) throws Exception {
    return mimeMessage(
        """
        Content-Type: multipart/mixed; boundary="b1"

        --b1
        Content-Type: text/plain
        Content-Disposition: attachment; filename="%s"

        %s
        --b1--
        """
            .formatted(fileName, content));
  }

  private MimeMessage mimeMessage(String source) throws Exception {
    return new MimeMessage(
        Session.getInstance(new Properties()),
        new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
  }
}
