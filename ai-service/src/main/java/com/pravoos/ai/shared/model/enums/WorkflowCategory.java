package com.pravoos.ai.shared.model.enums;

public enum WorkflowCategory {
  BANKRUPTCY("Банкротство"),
  DEBT_COLLECTION("Взыскание задолженности"),
  REGISTRATION("Регистрация"),
  CUSTOM("Пользовательский");

  private final String displayName;

  WorkflowCategory(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}
