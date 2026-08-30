package com.pravoos.ai.document.api;

import java.util.List;
import java.util.UUID;

public interface DocumentRetrieval {

  RetrievedChunks retrieveKnowledgeBase(String query, int topK);

  RetrievedChunks retrieveForCase(String query, int topK, UUID caseId, SearchActor actor);

  DocumentChunkMatches retrieveInDocument(
      List<String> queries, int topK, UUID documentId, SearchActor actor);
}
