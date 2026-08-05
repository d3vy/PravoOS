package com.pravoos.ai.shared.mail;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FetchedEmailMapper {

  private static final String HEADER_IN_REPLY_TO = "In-Reply-To";
  private static final String HEADER_REFERENCES = "References";
  private static final int MAX_MULTIPART_DEPTH = 10;

  private final int maxBodyLength;

  public FetchedEmailMapper(int maxBodyLength) {
    this.maxBodyLength = maxBodyLength;
  }

  public FetchedEmail map(Message message, long uid, long uidValidity, String host)
      throws MessagingException, IOException {
    StringBuilder body = new StringBuilder();
    appendBody(message, body, 0);

    return new FetchedEmail(
        resolveMessageId(message, uid, uidValidity, host),
        uid,
        message.getSubject(),
        firstAddress(message.getFrom()),
        addresses(message.getRecipients(Message.RecipientType.TO)),
        addresses(message.getRecipients(Message.RecipientType.CC)),
        body.toString().trim(),
        sentAt(message),
        countAttachments(message, 0),
        firstHeader(message, HEADER_IN_REPLY_TO),
        references(message));
  }

  private String resolveMessageId(Message message, long uid, long uidValidity, String host)
      throws MessagingException {
    if (message instanceof MimeMessage mimeMessage) {
      String messageId = mimeMessage.getMessageID();
      if (messageId != null && !messageId.isBlank()) {
        return messageId.trim();
      }
    }
    return "<uid-%d-%d@%s>".formatted(uidValidity, uid, host);
  }

  private void appendBody(Part part, StringBuilder body, int depth)
      throws MessagingException, IOException {
    if (depth > MAX_MULTIPART_DEPTH || body.length() >= maxBodyLength || isAttachment(part)) {
      return;
    }
    if (part.isMimeType("text/plain")) {
      append(body, asString(part.getContent()));
      return;
    }
    if (part.isMimeType("text/html")) {
      append(body, HtmlToTextConverter.convert(asString(part.getContent())));
      return;
    }
    if (part.getContent() instanceof Multipart multipart) {
      if (part.isMimeType("multipart/alternative")) {
        appendPreferredAlternative(multipart, body, depth);
        return;
      }
      for (int index = 0; index < multipart.getCount(); index++) {
        appendBody(multipart.getBodyPart(index), body, depth + 1);
      }
    }
  }

  private void appendPreferredAlternative(Multipart multipart, StringBuilder body, int depth)
      throws MessagingException, IOException {
    Part htmlPart = null;
    for (int index = 0; index < multipart.getCount(); index++) {
      Part candidate = multipart.getBodyPart(index);
      if (candidate.isMimeType("text/plain") && !isAttachment(candidate)) {
        appendBody(candidate, body, depth + 1);
        return;
      }
      if (candidate.isMimeType("text/html") && !isAttachment(candidate)) {
        htmlPart = candidate;
      }
    }
    if (htmlPart != null) {
      appendBody(htmlPart, body, depth + 1);
      return;
    }
    for (int index = 0; index < multipart.getCount(); index++) {
      appendBody(multipart.getBodyPart(index), body, depth + 1);
    }
  }

  private void append(StringBuilder body, String text) {
    if (text == null || text.isBlank()) {
      return;
    }
    if (!body.isEmpty()) {
      body.append("\n\n");
    }
    int remaining = maxBodyLength - body.length();
    if (remaining <= 0) {
      return;
    }
    body.append(text.length() <= remaining ? text : text.substring(0, remaining));
  }

  private int countAttachments(Part part, int depth) throws MessagingException, IOException {
    if (depth > MAX_MULTIPART_DEPTH) {
      return 0;
    }
    if (isAttachment(part)) {
      return 1;
    }
    if (part.getContent() instanceof Multipart multipart) {
      int total = 0;
      for (int index = 0; index < multipart.getCount(); index++) {
        total += countAttachments(multipart.getBodyPart(index), depth + 1);
      }
      return total;
    }
    return 0;
  }

  private boolean isAttachment(Part part) throws MessagingException {
    String disposition = part.getDisposition();
    if (disposition != null && disposition.equalsIgnoreCase(Part.ATTACHMENT)) {
      return true;
    }
    return part.getFileName() != null && !part.getFileName().isBlank();
  }

  private String asString(Object content) throws IOException {
    if (content instanceof String text) {
      return text;
    }
    if (content instanceof java.io.InputStream stream) {
      return new String(stream.readAllBytes());
    }
    return content == null ? "" : content.toString();
  }

  private LocalDateTime sentAt(Message message) throws MessagingException {
    Date date = message.getSentDate() != null ? message.getSentDate() : message.getReceivedDate();
    if (date == null) {
      return null;
    }
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneOffset.UTC);
  }

  private String firstAddress(Address[] addresses) {
    List<String> parsed = addresses(addresses);
    return parsed.isEmpty() ? null : parsed.getFirst();
  }

  private List<String> addresses(Address[] addresses) {
    if (addresses == null) {
      return List.of();
    }
    List<String> result = new ArrayList<>(addresses.length);
    for (Address address : addresses) {
      String value =
          address instanceof InternetAddress internetAddress
              ? internetAddress.getAddress()
              : address.toString();
      if (value != null && !value.isBlank()) {
        result.add(value.trim().toLowerCase(Locale.ROOT));
      }
    }
    return result;
  }

  private String firstHeader(Message message, String name) throws MessagingException {
    String[] values = message.getHeader(name);
    if (values == null || values.length == 0 || values[0] == null || values[0].isBlank()) {
      return null;
    }
    return values[0].trim();
  }

  private List<String> references(Message message) throws MessagingException {
    String header = firstHeader(message, HEADER_REFERENCES);
    if (header == null) {
      return List.of();
    }
    return Arrays.stream(header.split("\\s+")).filter(value -> !value.isBlank()).toList();
  }
}
