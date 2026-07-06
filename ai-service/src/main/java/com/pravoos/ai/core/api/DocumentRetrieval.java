package com.pravoos.ai.core.api;

import java.util.List;
import java.util.UUID;

public interface DocumentRetrieval {

    List<RetrievedChunk> retrieveKnowledgeBase(String query, int topK);

    List<RetrievedChunk> retrieveForCase(String query, int topK, UUID caseId);
}
