package com.pravoos.ai.shared.model.enums;

public enum WorkflowStepStatus {

    PENDING("Ожидает"),
    RUNNING("Выполняется"),
    COMPLETED("Завершён"),
    FAILED("Ошибка"),
    SKIPPED("Пропущен");

    private final String displayName;

    WorkflowStepStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
