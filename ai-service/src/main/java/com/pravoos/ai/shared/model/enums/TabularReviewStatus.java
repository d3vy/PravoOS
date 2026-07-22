package com.pravoos.ai.shared.model.enums;

public enum TabularReviewStatus {

    PENDING,
    RUNNING,
    COMPLETED,
    PARTIAL,
    FAILED;

    public boolean terminal() {
        return this == COMPLETED || this == PARTIAL || this == FAILED;
    }
}
