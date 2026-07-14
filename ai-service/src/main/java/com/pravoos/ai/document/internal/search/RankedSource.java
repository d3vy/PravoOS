package com.pravoos.ai.document.internal.search;

import java.util.List;

public record RankedSource(String name, List<ChunkCandidate> candidates, double weight) {

    public RankedSource {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        if (weight < 0) {
            throw new IllegalArgumentException("Source weight must not be negative: " + name);
        }
    }
}
