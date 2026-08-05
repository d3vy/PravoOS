package com.pravoos.common.web;

public enum AiProcessingMode {
  DISABLED,
  RU_ONLY,
  CROSS_BORDER;

  public static final AiProcessingMode DEFAULT = RU_ONLY;

  public static AiProcessingMode fromClaim(String claim) {
    if (claim == null || claim.isBlank()) {
      return DEFAULT;
    }
    try {
      return valueOf(claim.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      return DEFAULT;
    }
  }

  public boolean allowsCrossBorderTransfer() {
    return this == CROSS_BORDER;
  }

  public boolean allowsAi() {
    return this != DISABLED;
  }
}
