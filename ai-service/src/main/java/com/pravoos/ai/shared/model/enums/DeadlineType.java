package com.pravoos.ai.shared.model.enums;

public enum DeadlineType {

    FILING_DEADLINE("Срок подачи"),
    NEXT_HEARING("Судебное заседание"),
    EXPIRY("Истечение срока");

    private final String displayName;

    DeadlineType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
