package com.pravoos.ai.core.internal.repository.mongo;

import com.pravoos.ai.core.internal.model.mongo.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface ConversationRepository extends MongoRepository<Conversation, String> {

    List<Conversation> findTop100ByLawyerIdOrderByCreatedAtDesc(UUID lawyerId);

    List<Conversation> findTop50ByLawyerIdAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(UUID lawyerId, String title);

    Page<Conversation> findByLawyerIdOrderByCreatedAtDesc(UUID lawyerId, Pageable pageable);

    Page<Conversation> findByLawyerIdAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(UUID lawyerId, String title, Pageable pageable);

    List<Conversation> findByLawyerId(UUID lawyerId);

    void deleteByLawyerId(UUID lawyerId);
}
