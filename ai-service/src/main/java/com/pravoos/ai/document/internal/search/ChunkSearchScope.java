package com.pravoos.ai.document.internal.search;

import java.util.UUID;

public record ChunkSearchScope(UUID caseId, UUID documentId) {

  public static ChunkSearchScope knowledgeBase() {
    return new ChunkSearchScope(null, null);
  }

  public static ChunkSearchScope forCase(UUID caseId) {
    if (caseId == null) {
      throw new IllegalArgumentException("caseId must not be null for a case-scoped search");
    }
    return new ChunkSearchScope(caseId, null);
  }

  public static ChunkSearchScope forDocument(UUID documentId) {
    if (documentId == null) {
      throw new IllegalArgumentException(
          "documentId must not be null for a document-scoped search");
    }
    return new ChunkSearchScope(null, documentId);
  }

  public boolean caseScoped() {
    return caseId != null;
  }

  public boolean documentScoped() {
    return documentId != null;
  }
}
