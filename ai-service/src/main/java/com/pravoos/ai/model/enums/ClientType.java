package com.pravoos.ai.model.enums;

public enum ClientType {

    INDIVIDUAL("Физлицо"),
    COMPANY("Юрлицо");

    private final String displayName;

    ClientType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
