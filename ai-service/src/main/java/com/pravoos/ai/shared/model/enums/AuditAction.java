package com.pravoos.ai.shared.model.enums;

public enum AuditAction {

    DOCUMENT_DOWNLOAD("DOCUMENT"),
    CLIENT_VIEW("CLIENT");

    private final String resourceType;

    AuditAction(String resourceType) {
        this.resourceType = resourceType;
    }

    public String resourceType() {
        return resourceType;
    }
}
