package com.pravoos.ai.shared.mail;

public record FetchedAttachment(
    int partIndex,
    String fileName,
    String contentType,
    long sizeBytes,
    byte[] content,
    AttachmentSkipReason skipReason) {

  public static FetchedAttachment loaded(
      int partIndex, String fileName, String contentType, byte[] content) {
    return new FetchedAttachment(partIndex, fileName, contentType, content.length, content, null);
  }

  public static FetchedAttachment skipped(
      int partIndex,
      String fileName,
      String contentType,
      long sizeBytes,
      AttachmentSkipReason skipReason) {
    return new FetchedAttachment(partIndex, fileName, contentType, sizeBytes, null, skipReason);
  }

  public boolean isSkipped() {
    return skipReason != null;
  }
}
