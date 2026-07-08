package com.pravoos.ai.shared.model.enums;

public enum WorkflowRunStatus {

    RUNNING("Выполняется"),
    COMPLETED("Завершён"),
    FAILED("Ошибка");

    private final String displayName;

    WorkflowRunStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
