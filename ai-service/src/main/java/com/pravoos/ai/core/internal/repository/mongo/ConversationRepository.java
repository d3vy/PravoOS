package com.pravoos.ai.core.internal.repository.mongo;

import com.pravoos.ai.core.internal.model.mongo.Conversation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ConversationRepository
    extends MongoRepository<Conversation, String>, ConversationSearchRepository {

  List<Conversation> findByLawyerId(UUID lawyerId);

  void deleteByLawyerId(UUID lawyerId);
}
