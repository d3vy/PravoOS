package com.pravoos.ai.shared.model.enums;

public enum EmailLinkSource {
  CASE_NUMBER("Номер дела в теме"),
  THREAD("Переписка в том же треде"),
  ADDRESS("Адрес клиента"),
  MANUAL("Привязано вручную");

  private final String displayName;

  EmailLinkSource(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}
