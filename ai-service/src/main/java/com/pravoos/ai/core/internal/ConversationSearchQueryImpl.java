package com.pravoos.ai.core.internal;

import com.pravoos.ai.core.api.ConversationSearchQuery;
import com.pravoos.ai.core.internal.repository.mongo.ConversationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationSearchQueryImpl implements ConversationSearchQuery {

  private final ConversationRepository conversationRepository;

  public ConversationSearchQueryImpl(ConversationRepository conversationRepository) {
    this.conversationRepository = conversationRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ConversationSearchHit> searchConversations(UUID lawyerId, String query, int limit) {
    return conversationRepository
        .findTop50ByLawyerIdAndCaseIdIsNullAndDocumentIdIsNullAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(
            lawyerId, query)
        .stream()
        .limit(limit)
        .map(c -> new ConversationSearchHit(c.getId(), c.getTitle()))
        .toList();
  }
}
