package com.pravoos.ai.repository.mongo;

import com.pravoos.ai.model.mongo.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface ConversationRepository extends MongoRepository<Conversation, String> {

    List<Conversation> findTop100ByLawyerIdOrderByCreatedAtDesc(UUID lawyerId);
}
