package com.pravoos.ai.core.internal.recyclebin;

import com.pravoos.ai.core.internal.model.mongo.Conversation;
import com.pravoos.ai.core.internal.repository.mongo.ConversationRepository;
import com.pravoos.ai.core.internal.repository.mongo.MessageRepository;
import com.pravoos.ai.recyclebin.api.BinContents;
import com.pravoos.ai.recyclebin.api.BinSnapshot;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.recyclebin.api.SoftDeleteStore;
import com.pravoos.ai.shared.exception.ConversationNotFoundException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ConversationSoftDeleteStore implements SoftDeleteStore {

  private static final String UNTITLED_CONVERSATION = "Диалог без названия";

  private final ConversationRepository conversationRepository;
  private final MessageRepository messageRepository;

  public ConversationSoftDeleteStore(
      ConversationRepository conversationRepository, MessageRepository messageRepository) {
    this.conversationRepository = conversationRepository;
    this.messageRepository = messageRepository;
  }

  @Override
  public RecycleBinEntityType entityType() {
    return RecycleBinEntityType.CONVERSATION;
  }

  @Override
  public BinContents moveToBin(String entityId, DeletionActor actor, boolean cascade) {
    Conversation conversation =
        conversationRepository
            .findActiveById(entityId)
            .orElseThrow(() -> new ConversationNotFoundException(entityId));
    conversationRepository.softDelete(
        entityId, conversation.getLawyerId(), LocalDateTime.now(ZoneOffset.UTC));
    return BinContents.of(
        new BinSnapshot(
            RecycleBinEntityType.CONVERSATION,
            entityId,
            title(conversation),
            conversation.getLawyerId(),
            conversation.getOrgId(),
            payload(conversation)));
  }

  @Override
  public void restore(String entityId) {
    conversationRepository.restore(entityId);
  }

  @Override
  public void purge(String entityId) {
    messageRepository.deleteByConversationIdIn(List.of(entityId));
    conversationRepository.deleteById(entityId);
  }

  private static String title(Conversation conversation) {
    String title = conversation.getTitle();
    return title == null || title.isBlank() ? UNTITLED_CONVERSATION : title;
  }

  private static Map<String, Object> payload(Conversation conversation) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("createdAt", String.valueOf(conversation.getCreatedAt()));
    payload.put("updatedAt", String.valueOf(conversation.getUpdatedAt()));
    if (conversation.getCaseId() != null) {
      payload.put("caseId", conversation.getCaseId().toString());
    }
    if (conversation.getDocumentId() != null) {
      payload.put("documentId", conversation.getDocumentId().toString());
    }
    return payload;
  }
}
