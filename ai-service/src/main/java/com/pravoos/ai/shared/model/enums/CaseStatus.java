package com.pravoos.ai.shared.model.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum CaseStatus {
  INTAKE("Приём"),
  IN_PROGRESS("В работе"),
  SUBMITTED("Подано в суд"),
  CLOSED_WON("Закрыто — выиграно"),
  CLOSED_LOST("Закрыто — проиграно");

  public static final Set<CaseStatus> CLOSED =
      Collections.unmodifiableSet(EnumSet.of(CLOSED_WON, CLOSED_LOST));

  private final String displayName;

  CaseStatus(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isClosed() {
    return CLOSED.contains(this);
  }
}
