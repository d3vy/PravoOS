package com.pravoos.ai.core.internal;

import com.pravoos.ai.core.api.ConversationSearchQuery;
import com.pravoos.ai.core.internal.repository.mongo.ConversationRepository;
import com.pravoos.ai.shared.util.PageRequests;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ConversationSearchQueryImpl implements ConversationSearchQuery {

  private final ConversationRepository conversationRepository;

  public ConversationSearchQueryImpl(ConversationRepository conversationRepository) {
    this.conversationRepository = conversationRepository;
  }

  @Override
  public List<ConversationSearchHit> searchConversations(UUID lawyerId, String query, int limit) {
    return conversationRepository
        .searchForLawyer(lawyerId, null, null, query, PageRequests.of(0, limit))
        .getContent()
        .stream()
        .map(c -> new ConversationSearchHit(c.getId(), c.getTitle()))
        .toList();
  }
}
