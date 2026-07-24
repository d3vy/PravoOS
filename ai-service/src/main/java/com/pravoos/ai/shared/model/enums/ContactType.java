package com.pravoos.ai.shared.model.enums;

public enum ContactType {
  CALL("Звонок"),
  MEETING("Встреча"),
  LETTER("Письмо"),
  EMAIL("Эл. письмо"),
  MESSENGER("Мессенджер");

  private final String displayName;

  ContactType(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}
