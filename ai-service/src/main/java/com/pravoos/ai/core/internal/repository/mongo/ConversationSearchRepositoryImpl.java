package com.pravoos.ai.core.internal.repository.mongo;

import com.pravoos.ai.core.internal.model.mongo.Conversation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public class ConversationSearchRepositoryImpl implements ConversationSearchRepository {

  private static final Sort RECENT_FIRST = Sort.by(Sort.Direction.DESC, "updatedAt");

  private final MongoTemplate mongoTemplate;

  public ConversationSearchRepositoryImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public Page<Conversation> searchForLawyer(
      UUID lawyerId, UUID caseId, UUID documentId, String titleQuery, Pageable pageable) {
    Criteria criteria = activeCriteria().and("lawyerId").is(lawyerId);
    if (caseId != null) {
      criteria.and("caseId").is(caseId);
    } else if (documentId != null) {
      criteria.and("documentId").is(documentId);
    } else {
      criteria.and("caseId").is(null).and("documentId").is(null);
    }
    return findPage(withTitle(criteria, titleQuery), pageable);
  }

  @Override
  public Page<Conversation> searchForAdmin(
      UUID orgId, UUID lawyerId, String titleQuery, Pageable pageable) {
    Criteria criteria = activeCriteria();
    if (orgId != null) {
      criteria.and("orgId").is(orgId);
    }
    if (lawyerId != null) {
      criteria.and("lawyerId").is(lawyerId);
    }
    return findPage(withTitle(criteria, titleQuery), pageable);
  }

  @Override
  public Optional<Conversation> findActiveById(String conversationId) {
    Query query = Query.query(activeCriteria().and("id").is(conversationId));
    return Optional.ofNullable(mongoTemplate.findOne(query, Conversation.class));
  }

  @Override
  public void touch(String conversationId, LocalDateTime updatedAt) {
    mongoTemplate.updateFirst(
        Query.query(Criteria.where("id").is(conversationId)),
        new Update().set("updatedAt", updatedAt),
        Conversation.class);
  }

  @Override
  public boolean softDelete(String conversationId, UUID lawyerId, LocalDateTime deletedAt) {
    Query query =
        Query.query(activeCriteria().and("id").is(conversationId).and("lawyerId").is(lawyerId));
    return mongoTemplate
            .updateFirst(query, new Update().set("deletedAt", deletedAt), Conversation.class)
            .getModifiedCount()
        > 0;
  }

  @Override
  public void restore(String conversationId) {
    mongoTemplate.updateFirst(
        Query.query(Criteria.where("id").is(conversationId)),
        new Update().unset("deletedAt"),
        Conversation.class);
  }

  @Override
  public Optional<Conversation> findDeletedById(String conversationId) {
    Query query = Query.query(Criteria.where("id").is(conversationId).and("deletedAt").ne(null));
    return Optional.ofNullable(mongoTemplate.findOne(query, Conversation.class));
  }

  private Criteria activeCriteria() {
    return Criteria.where("deletedAt").is(null);
  }

  private Criteria withTitle(Criteria criteria, String titleQuery) {
    if (titleQuery == null || titleQuery.isBlank()) {
      return criteria;
    }
    return criteria
        .and("title")
        .regex(Pattern.compile(Pattern.quote(titleQuery.trim()), Pattern.CASE_INSENSITIVE));
  }

  private Page<Conversation> findPage(Criteria criteria, Pageable pageable) {
    Pageable sorted = pageableWithRecentFirst(pageable);
    Query query = Query.query(criteria).with(sorted);
    List<Conversation> conversations = mongoTemplate.find(query, Conversation.class);
    long total = mongoTemplate.count(Query.query(criteria), Conversation.class);
    return new PageImpl<>(conversations, sorted, total);
  }

  private Pageable pageableWithRecentFirst(Pageable pageable) {
    return pageable.getSort().isSorted()
        ? pageable
        : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), RECENT_FIRST);
  }
}
