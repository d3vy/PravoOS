package com.pravoos.ai.shared.model.enums;

public enum AuditAction {
  DOCUMENT_DOWNLOAD("DOCUMENT"),
  CLIENT_VIEW("CLIENT"),
  CONSENT_GRANT("CLIENT"),
  CONSENT_REVOKE("CLIENT"),
  PERSONAL_DATA_EXPORT("CLIENT"),
  AI_CONVERSATION_VIEW("CONVERSATION");

  private final String resourceType;

  AuditAction(String resourceType) {
    this.resourceType = resourceType;
  }

  public String resourceType() {
    return resourceType;
  }
}
