package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.core.internal.dto.AdminConversationResponse;
import com.pravoos.ai.core.internal.dto.MessageResponse;
import com.pravoos.ai.core.internal.model.mongo.Conversation;
import com.pravoos.ai.core.internal.model.mongo.Message;
import com.pravoos.ai.core.internal.repository.mongo.ConversationRepository;
import com.pravoos.ai.core.internal.repository.mongo.MessageRepository;
import com.pravoos.ai.shared.exception.ConversationNotFoundException;
import com.pravoos.ai.shared.util.PageRequests;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;

@Service
public class AdminConversationService {

  private final ConversationRepository conversationRepository;
  private final MessageRepository messageRepository;

  public AdminConversationService(
      ConversationRepository conversationRepository, MessageRepository messageRepository) {
    this.conversationRepository = conversationRepository;
    this.messageRepository = messageRepository;
  }

  public Page<AdminConversationResponse> getConversations(
      UUID orgId, UUID lawyerId, String query, int page, int size) {
    return conversationRepository
        .searchForAdmin(orgId, lawyerId, query, PageRequests.of(page, size))
        .map(AdminConversationResponse::from);
  }

  public Page<MessageResponse> getMessages(String conversationId, int page, int size) {
    Conversation conversation =
        conversationRepository
            .findActiveById(conversationId)
            .orElseThrow(() -> new ConversationNotFoundException(conversationId));

    Page<Message> messages =
        messageRepository.findByConversationIdOrderByCreatedAtDesc(
            conversation.getId(), PageRequests.of(page, size));
    List<MessageResponse> chronological =
        new ArrayList<>(messages.getContent().stream().map(MessageResponse::from).toList());
    Collections.reverse(chronological);
    return new PageImpl<>(chronological, messages.getPageable(), messages.getTotalElements());
  }
}
