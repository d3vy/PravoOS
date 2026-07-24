package com.pravoos.ai.document.internal.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

public final class ClamdVersionParser {

  private static final DateTimeFormatter SIGNATURE_TIMESTAMP =
      DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy", Locale.ENGLISH);

  private ClamdVersionParser() {}

  public static Optional<LocalDate> signatureDate(String versionResponse) {
    if (versionResponse == null) {
      return Optional.empty();
    }
    String[] parts = versionResponse.trim().split("/");
    if (parts.length < 3) {
      return Optional.empty();
    }
    String timestamp = parts[2].trim().replaceAll("\\s+", " ");
    try {
      return Optional.of(LocalDate.parse(timestamp, SIGNATURE_TIMESTAMP));
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }
}
