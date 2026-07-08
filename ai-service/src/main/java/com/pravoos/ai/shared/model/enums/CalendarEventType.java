package com.pravoos.ai.shared.model.enums;

public enum CalendarEventType {

    DEADLINE("Дедлайн"),
    HEARING("Заседание"),
    TASK("Задача");

    private final String displayName;

    CalendarEventType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
