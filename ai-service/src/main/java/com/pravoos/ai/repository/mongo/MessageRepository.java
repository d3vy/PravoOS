package com.pravoos.ai.repository.mongo;

import com.pravoos.ai.model.mongo.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MessageRepository extends MongoRepository<Message, String> {

    List<Message> findByConversationIdOrderByCreatedAt(String conversationId);

    Page<Message> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable pageable);

    List<Message> findTop10ByConversationIdOrderByCreatedAtDesc(String conversationId);

    void deleteByConversationIdIn(List<String> conversationIds);
}
