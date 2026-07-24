package com.pravoos.ai.shared.model.enums;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum ReviewAnswerConfidence {
  HIGH("Высокая"),
  MEDIUM("Средняя"),
  LOW("Низкая"),
  NOT_FOUND("Не найдено");

  private static final Logger log = LoggerFactory.getLogger(ReviewAnswerConfidence.class);

  private final String displayName;

  ReviewAnswerConfidence(String displayName) {
    this.displayName = displayName;
  }

  public String displayName() {
    return displayName;
  }

  public static ReviewAnswerConfidence fromString(String value) {
    if (value == null) {
      return LOW;
    }
    return switch (value.trim().toUpperCase()) {
      case "HIGH", "ВЫСОКАЯ" -> HIGH;
      case "MEDIUM", "СРЕДНЯЯ" -> MEDIUM;
      case "LOW", "НИЗКАЯ" -> LOW;
      case "NOT_FOUND", "NONE", "НЕ НАЙДЕНО" -> NOT_FOUND;
      default -> {
        log.warn("Unknown review answer confidence '{}', falling back to LOW", value);
        yield LOW;
      }
    };
  }
}
