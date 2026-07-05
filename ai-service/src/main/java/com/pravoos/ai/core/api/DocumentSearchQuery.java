package com.pravoos.ai.core.api;

import java.util.List;
import java.util.UUID;

public interface DocumentSearchQuery {

    List<DocumentSearchHit> searchDocuments(UUID lawyerId, String query, boolean searchContent, int limit);

    List<ConversationSearchHit> searchConversations(UUID lawyerId, String query, int limit);

    record DocumentSearchHit(UUID id, String title, String fileName, UUID caseId, String snippet) {
    }

    record ConversationSearchHit(String id, String title) {
    }
}
