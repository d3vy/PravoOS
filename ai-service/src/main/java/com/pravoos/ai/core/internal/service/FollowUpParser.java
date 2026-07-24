package com.pravoos.ai.core.internal.service;

import java.util.Arrays;
import java.util.List;

public final class FollowUpParser {

  static final String DELIMITER = "##FOLLOWUPS##";

  private FollowUpParser() {}

  public record ParsedAnswer(String answer, List<String> followUps) {}

  public static ParsedAnswer parse(String rawContent) {
    if (rawContent == null || rawContent.isBlank()) {
      return new ParsedAnswer("", List.of());
    }
    int delimiterIdx = rawContent.indexOf(DELIMITER);
    if (delimiterIdx == -1) {
      return new ParsedAnswer(rawContent.trim(), List.of());
    }

    String answer = rawContent.substring(0, delimiterIdx).trim();
    String followUpSection = rawContent.substring(delimiterIdx + DELIMITER.length()).trim();

    List<String> followUps =
        Arrays.stream(followUpSection.split("\\r?\\n"))
            .map(line -> line.replaceAll("^\\d+[.)\\s]+", "").trim())
            .filter(line -> !line.isBlank())
            .limit(3)
            .toList();

    return new ParsedAnswer(answer, followUps);
  }
}
