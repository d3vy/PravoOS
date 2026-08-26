package com.pravoos.ai.core.internal.repository.mongo;

import com.pravoos.ai.core.internal.model.mongo.Conversation;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ConversationSearchRepository {

  Page<Conversation> searchForLawyer(
      UUID lawyerId, UUID caseId, UUID documentId, String titleQuery, Pageable pageable);

  Page<Conversation> searchForAdmin(
      UUID orgId, UUID lawyerId, String titleQuery, Pageable pageable);

  Optional<Conversation> findActiveById(String conversationId);

  void touch(String conversationId, LocalDateTime updatedAt);

  boolean softDelete(String conversationId, UUID lawyerId, LocalDateTime deletedAt);
}
