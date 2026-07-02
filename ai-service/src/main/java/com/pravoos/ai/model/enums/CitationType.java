package com.pravoos.ai.model.enums;

public enum CitationType {

    COURT_CASE("Судебное дело"),
    STATUTE("Норма права");

    private final String displayName;

    CitationType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
