package com.pravoos.ai.document.internal.search;

import java.util.List;

public interface Reranker {

    List<ChunkCandidate> rerank(String query, List<ChunkCandidate> candidates, int topK);
}
