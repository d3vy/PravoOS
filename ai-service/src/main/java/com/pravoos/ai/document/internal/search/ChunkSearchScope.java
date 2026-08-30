package com.pravoos.ai.document.internal.search;

import com.pravoos.ai.document.api.SearchActor;
import java.util.UUID;

public record ChunkSearchScope(UUID caseId, UUID documentId, SearchActor actor) {

  public ChunkSearchScope {
    if ((caseId != null || documentId != null) && actor == null) {
      throw new IllegalArgumentException("actor must not be null for a tenant-scoped search");
    }
  }

  public static ChunkSearchScope knowledgeBase() {
    return new ChunkSearchScope(null, null, null);
  }

  public static ChunkSearchScope forCase(UUID caseId, SearchActor actor) {
    if (caseId == null) {
      throw new IllegalArgumentException("caseId must not be null for a case-scoped search");
    }
    return new ChunkSearchScope(caseId, null, actor);
  }

  public static ChunkSearchScope forDocument(UUID documentId, SearchActor actor) {
    if (documentId == null) {
      throw new IllegalArgumentException(
          "documentId must not be null for a document-scoped search");
    }
    return new ChunkSearchScope(null, documentId, actor);
  }

  public boolean caseScoped() {
    return caseId != null;
  }

  public boolean documentScoped() {
    return documentId != null;
  }
}
