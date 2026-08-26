package com.pravoos.ai.core.api;

import java.util.UUID;

public record PageContextScope(UUID caseId, UUID documentId, String label) {

  private static final PageContextScope NONE = new PageContextScope(null, null, null);

  public static PageContextScope none() {
    return NONE;
  }

  public static PageContextScope ofCase(UUID caseId, String label) {
    return new PageContextScope(caseId, null, label);
  }

  public static PageContextScope ofLabel(String label) {
    return new PageContextScope(null, null, label);
  }

  public boolean isEmpty() {
    return caseId == null && documentId == null && (label == null || label.isBlank());
  }
}
