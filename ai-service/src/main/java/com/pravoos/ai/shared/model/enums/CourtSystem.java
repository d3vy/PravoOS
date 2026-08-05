package com.pravoos.ai.shared.model.enums;

public enum CourtSystem {
  ARBITR("КАД.Арбитр", "https://kad.arbitr.ru/Card/"),
  GENERAL_JURISDICTION("ГАС «Правосудие»", null);

  private final String displayName;
  private final String cardBaseUrl;

  CourtSystem(String displayName, String cardBaseUrl) {
    this.displayName = displayName;
    this.cardBaseUrl = cardBaseUrl;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String cardUrl(String caseGuid) {
    if (cardBaseUrl == null || caseGuid == null || caseGuid.isBlank()) {
      return null;
    }
    return cardBaseUrl + caseGuid.trim();
  }
}
