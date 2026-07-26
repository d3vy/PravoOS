package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.core.internal.dto.DocumentInsightResponse;
import com.pravoos.ai.document.api.DocumentAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DocumentInsightService {

  private final DocumentAccess documentAccess;
  private final DocumentAccessGuard documentAccessGuard;

  public DocumentInsightService(
      DocumentAccess documentAccess, DocumentAccessGuard documentAccessGuard) {
    this.documentAccess = documentAccess;
    this.documentAccessGuard = documentAccessGuard;
  }

  public DocumentInsightResponse summary(UUID documentId, UUID userId, List<UUID> orgIds) {
    return DocumentInsightResponse.from(
        documentAccessGuard.requireVisible(documentId, userId, orgIds));
  }

  public DocumentInsightResponse regenerate(UUID documentId, UUID userId, List<UUID> orgIds) {
    documentAccessGuard.requireVisible(documentId, userId, orgIds);
    return DocumentInsightResponse.from(documentAccess.regenerateSummary(documentId, userId));
  }
}
