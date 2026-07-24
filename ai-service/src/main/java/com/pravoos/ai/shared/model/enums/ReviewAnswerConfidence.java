package com.pravoos.ai.shared.model.enums;

public enum ReviewAnswerConfidence {
  HIGH("Высокая"),
  MEDIUM("Средняя"),
  LOW("Низкая"),
  NOT_FOUND("Не найдено");

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
      case "NOT_FOUND", "NONE", "НЕ НАЙДЕНО" -> NOT_FOUND;
      default -> LOW;
    };
  }
}
