package com.pravoos.ai.document.api;

import java.util.UUID;

public record DocumentChunkMatch(
        UUID chunkId,
        int chunkIndex,
        String content,
        double score) {}
