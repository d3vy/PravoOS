package com.pravoos.ai.document.internal.search;

import java.util.UUID;

public record ChunkSearchScope(UUID caseId) {

    public static ChunkSearchScope knowledgeBase() {
        return new ChunkSearchScope(null);
    }

    public static ChunkSearchScope forCase(UUID caseId) {
        if (caseId == null) {
            throw new IllegalArgumentException("caseId must not be null for a case-scoped search");
        }
        return new ChunkSearchScope(caseId);
    }

    public boolean caseScoped() {
        return caseId != null;
    }
}
