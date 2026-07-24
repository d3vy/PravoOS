package com.pravoos.ai.core.internal.repository.mongo;

import com.pravoos.ai.core.internal.model.mongo.Message;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MessageRepository extends MongoRepository<Message, String> {

  List<Message> findByConversationIdOrderByCreatedAt(String conversationId);

  Page<Message> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable pageable);

  List<Message> findTop10ByConversationIdOrderByCreatedAtDesc(String conversationId);

  void deleteByConversationIdIn(List<String> conversationIds);
}
