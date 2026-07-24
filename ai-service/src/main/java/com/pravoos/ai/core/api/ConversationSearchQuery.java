package com.pravoos.ai.core.api;

import java.util.List;
import java.util.UUID;

public interface ConversationSearchQuery {

  List<ConversationSearchHit> searchConversations(UUID lawyerId, String query, int limit);

  record ConversationSearchHit(String id, String title) {}
}
