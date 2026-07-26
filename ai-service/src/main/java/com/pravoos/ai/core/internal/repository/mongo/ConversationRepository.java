package com.pravoos.ai.core.internal.repository.mongo;

import com.pravoos.ai.core.internal.model.mongo.Conversation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ConversationRepository extends MongoRepository<Conversation, String> {

  List<Conversation> findTop100ByLawyerIdOrderByCreatedAtDesc(UUID lawyerId);

  List<Conversation>
      findTop50ByLawyerIdAndCaseIdIsNullAndDocumentIdIsNullAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(
          UUID lawyerId, String title);

  Page<Conversation> findByLawyerIdAndCaseIdIsNullAndDocumentIdIsNullOrderByCreatedAtDesc(
      UUID lawyerId, Pageable pageable);

  Page<Conversation>
      findByLawyerIdAndCaseIdIsNullAndDocumentIdIsNullAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(
          UUID lawyerId, String title, Pageable pageable);

  Page<Conversation> findByLawyerIdAndDocumentIdOrderByCreatedAtDesc(
      UUID lawyerId, UUID documentId, Pageable pageable);

  Page<Conversation> findByLawyerIdAndDocumentIdAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(
      UUID lawyerId, UUID documentId, String title, Pageable pageable);

  Page<Conversation> findByLawyerIdAndCaseIdOrderByCreatedAtDesc(
      UUID lawyerId, UUID caseId, Pageable pageable);

  Page<Conversation> findByLawyerIdAndCaseIdAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(
      UUID lawyerId, UUID caseId, String title, Pageable pageable);

  List<Conversation> findByLawyerId(UUID lawyerId);

  void deleteByLawyerId(UUID lawyerId);
}
