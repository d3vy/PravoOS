package com.pravoos.ai.document.api;

import java.util.List;
import java.util.UUID;

public interface DocumentSearchQuery {

  List<DocumentSearchHit> searchDocuments(
      UUID lawyerId, String query, boolean searchContent, int limit);

  record DocumentSearchHit(UUID id, String title, String fileName, UUID caseId, String snippet) {}
}
