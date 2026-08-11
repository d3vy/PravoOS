package com.pravoos.ai.document.internal.search;

import java.util.List;

public interface Reranker {

  RerankOutcome rerank(String query, List<ChunkCandidate> candidates, int topK);
}
