package com.pravoos.ai.shared.model.enums;

public enum CitationStatus {
  VERIFIED("Подтверждено"),
  NOT_FOUND("Не найдено"),
  OUTDATED("Редакция устарела"),
  UNVERIFIED("Не проверено");

  private final String displayName;

  CitationStatus(String displayName) {
    this.displayName = displayName;
  }

  public String displayName() {
    return displayName;
  }
}
