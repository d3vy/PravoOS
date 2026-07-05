package com.pravoos.ai.shared.model.enums;

public enum CaseStatus {

    INTAKE("Приём"),
    IN_PROGRESS("В работе"),
    SUBMITTED("Подано в суд"),
    CLOSED_WON("Закрыто — выиграно"),
    CLOSED_LOST("Закрыто — проиграно");

    private final String displayName;

    CaseStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
