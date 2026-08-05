package com.pravoos.ai.shared.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeUtility;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AttachmentExtractor {

  private static final Logger log = LoggerFactory.getLogger(AttachmentExtractor.class);
  private static final int MAX_MULTIPART_DEPTH = 10;
  private static final int READ_BUFFER_SIZE = 8192;
  private static final int MAX_FILE_NAME_LENGTH = 255;
  private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

  private final int maxPerMessage;
  private final long maxBytes;

  public AttachmentExtractor(int maxPerMessage, long maxBytes) {
    this.maxPerMessage = maxPerMessage;
    this.maxBytes = maxBytes;
  }

  public List<FetchedAttachment> extract(Part message) throws MessagingException, IOException {
    List<FetchedAttachment> attachments = new ArrayList<>();
    collect(message, attachments, 0);
    return attachments;
  }

  private void collect(Part part, List<FetchedAttachment> attachments, int depth)
      throws MessagingException, IOException {
    if (depth > MAX_MULTIPART_DEPTH) {
      return;
    }
    if (isAttachment(part)) {
      attachments.add(toAttachment(part, attachments.size()));
      return;
    }
    if (part.getContent() instanceof Multipart multipart) {
      for (int index = 0; index < multipart.getCount(); index++) {
        collect(multipart.getBodyPart(index), attachments, depth + 1);
      }
    }
  }

  private FetchedAttachment toAttachment(Part part, int partIndex) throws MessagingException {
    String fileName = fileNameOf(part, partIndex);
    String contentType = contentTypeOf(part);
    long declaredSize = Math.max(part.getSize(), 0);

    if (partIndex >= maxPerMessage) {
      return FetchedAttachment.skipped(
          partIndex, fileName, contentType, declaredSize, AttachmentSkipReason.LIMIT_EXCEEDED);
    }

    byte[] content;
    try {
      content = readBounded(part);
    } catch (IOException | MessagingException e) {
      log.warn("Вложение '{}' не удалось прочитать: {}", fileName, e.getMessage());
      return FetchedAttachment.skipped(
          partIndex, fileName, contentType, declaredSize, AttachmentSkipReason.UNREADABLE);
    }
    if (content == null) {
      return FetchedAttachment.skipped(
          partIndex, fileName, contentType, declaredSize, AttachmentSkipReason.TOO_LARGE);
    }
    return FetchedAttachment.loaded(partIndex, fileName, contentType, content);
  }

  private byte[] readBounded(Part part) throws MessagingException, IOException {
    try (InputStream input = part.getInputStream()) {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] chunk = new byte[READ_BUFFER_SIZE];
      int read;
      while ((read = input.read(chunk)) != -1) {
        if (buffer.size() + read > maxBytes) {
          return null;
        }
        buffer.write(chunk, 0, read);
      }
      return buffer.toByteArray();
    }
  }

  private boolean isAttachment(Part part) throws MessagingException {
    String disposition = part.getDisposition();
    if (disposition != null && disposition.equalsIgnoreCase(Part.ATTACHMENT)) {
      return true;
    }
    return part.getFileName() != null && !part.getFileName().isBlank();
  }

  private String fileNameOf(Part part, int partIndex) throws MessagingException {
    String rawName = part.getFileName();
    if (rawName == null || rawName.isBlank()) {
      return "attachment-" + (partIndex + 1);
    }
    String decoded = decode(rawName).trim();
    String baseName =
        decoded.substring(Math.max(decoded.lastIndexOf('/'), decoded.lastIndexOf('\\')) + 1);
    if (baseName.isBlank() || baseName.equals(".") || baseName.equals("..")) {
      return "attachment-" + (partIndex + 1);
    }
    return baseName.length() <= MAX_FILE_NAME_LENGTH
        ? baseName
        : baseName.substring(0, MAX_FILE_NAME_LENGTH);
  }

  private String decode(String rawName) {
    try {
      return MimeUtility.decodeText(rawName);
    } catch (UnsupportedEncodingException e) {
      return rawName;
    }
  }

  private String contentTypeOf(Part part) throws MessagingException {
    String contentType = part.getContentType();
    if (contentType == null || contentType.isBlank()) {
      return DEFAULT_CONTENT_TYPE;
    }
    int separator = contentType.indexOf(';');
    String withoutParameters = separator > 0 ? contentType.substring(0, separator) : contentType;
    return withoutParameters.trim().toLowerCase(Locale.ROOT);
  }
}
