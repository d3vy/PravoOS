package com.pravoos.ai.repository;

import com.pravoos.ai.model.mongo.Message;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MessageRepository extends MongoRepository<Message, String> {

    List<Message> findByConversationIdOrderByCreatedAt(String conversationId);
}
