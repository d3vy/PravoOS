package com.pravoos.ai.shared.model.enums;

public enum AuditAction {
  DOCUMENT_DOWNLOAD("DOCUMENT"),
  CLIENT_VIEW("CLIENT"),
  CONSENT_GRANT("CLIENT"),
  CONSENT_REVOKE("CLIENT"),
  PERSONAL_DATA_EXPORT("CLIENT"),
  AI_CONVERSATION_VIEW("CONVERSATION"),
  RECYCLE_BIN_RESTORE("RECYCLE_BIN"),
  RECYCLE_BIN_PURGE("RECYCLE_BIN"),
  AI_ACTION_PROPOSE("AI_ACTION"),
  AI_ACTION_APPROVE("AI_ACTION"),
  AI_ACTION_REJECT("AI_ACTION"),
  AI_TOOL_TRUST_GRANT("AI_ACTION"),
  AI_TOOL_TRUST_REVOKE("AI_ACTION");

  private final String resourceType;

  AuditAction(String resourceType) {
    this.resourceType = resourceType;
  }

  public String resourceType() {
    return resourceType;
  }
}
