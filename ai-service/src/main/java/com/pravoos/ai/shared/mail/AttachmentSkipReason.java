package com.pravoos.ai.shared.mail;

public enum AttachmentSkipReason {
  TOO_LARGE("Вложение превышает допустимый размер"),
  LIMIT_EXCEEDED("Превышено число вложений, импортируемых из одного письма"),
  UNREADABLE("Вложение не удалось прочитать с почтового сервера");

  private final String message;

  AttachmentSkipReason(String message) {
    this.message = message;
  }

  public String message() {
    return message;
  }
}
