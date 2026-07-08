package com.pravoos.ai.shared.model.enums;

public enum WorkflowStepType {

    AI_ANALYSIS("AI-анализ"),
    GENERATE_DRAFT("Генерация черновика"),
    GENERATE_TASKS("Задачи по чеклисту"),
    SET_DEADLINE("Дедлайн");

    private final String displayName;

    WorkflowStepType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
