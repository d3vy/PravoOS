package com.pravoos.ai.core.internal.config;

import com.pravoos.ai.core.internal.model.mongo.Conversation;
import com.pravoos.ai.core.internal.model.mongo.Message;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Configuration
public class MongoIndexConfig {

  private static final Logger log = LoggerFactory.getLogger(MongoIndexConfig.class);
  private static final String MESSAGES_TTL_INDEX = "messages_created_ttl";
  private static final String CONVERSATIONS_TTL_INDEX = "conversations_created_ttl";

  private final int retentionDays;

  public MongoIndexConfig(@Value("${chat.retention-days:0}") int retentionDays) {
    this.retentionDays = retentionDays;
  }

  @Bean
  public ApplicationRunner mongoIndexInitializer(MongoTemplate mongoTemplate) {
    return args -> {
      mongoTemplate
          .indexOps(Message.class)
          .ensureIndex(
              new Index()
                  .on("conversationId", Sort.Direction.ASC)
                  .on("createdAt", Sort.Direction.ASC)
                  .named("messages_conversation_created"));

      backfillConversationUpdatedAt(mongoTemplate);

      mongoTemplate
          .indexOps(Conversation.class)
          .ensureIndex(
              new Index()
                  .on("lawyerId", Sort.Direction.ASC)
                  .on("updatedAt", Sort.Direction.DESC)
                  .named("conversations_lawyer_updated"));

      mongoTemplate
          .indexOps(Conversation.class)
          .ensureIndex(
              new Index()
                  .on("lawyerId", Sort.Direction.ASC)
                  .on("caseId", Sort.Direction.ASC)
                  .on("updatedAt", Sort.Direction.DESC)
                  .named("conversations_lawyer_case_updated"));

      mongoTemplate
          .indexOps(Conversation.class)
          .ensureIndex(
              new Index()
                  .on("lawyerId", Sort.Direction.ASC)
                  .on("documentId", Sort.Direction.ASC)
                  .on("updatedAt", Sort.Direction.DESC)
                  .named("conversations_lawyer_document_updated"));

      mongoTemplate
          .indexOps(Conversation.class)
          .ensureIndex(
              new Index()
                  .on("orgId", Sort.Direction.ASC)
                  .on("updatedAt", Sort.Direction.DESC)
                  .named("conversations_org_updated"));

      dropIfExists(mongoTemplate, Message.class, "conversationId_1");
      dropIfExists(mongoTemplate, Conversation.class, "lawyerId_1");
      dropIfExists(mongoTemplate, Conversation.class, "conversations_lawyer_created");

      applyRetentionPolicy(mongoTemplate);

      log.info("MongoDB indexes ensured (messages, conversations)");
    };
  }

  private void backfillConversationUpdatedAt(MongoTemplate mongoTemplate) {
    long updated =
        mongoTemplate
            .updateMulti(
                Query.query(Criteria.where("updatedAt").is(null)),
                AggregationUpdate.update().set("updatedAt").toValue("$createdAt"),
                Conversation.class)
            .getModifiedCount();
    if (updated > 0) {
      log.info("Backfilled updatedAt on {} conversation(s)", updated);
    }
  }

  private void applyRetentionPolicy(MongoTemplate mongoTemplate) {
    if (retentionDays <= 0) {
      dropIfExists(mongoTemplate, Message.class, MESSAGES_TTL_INDEX);
      dropIfExists(mongoTemplate, Conversation.class, CONVERSATIONS_TTL_INDEX);
      log.info("Chat retention TTL disabled (chat.retention-days={})", retentionDays);
      return;
    }
    Duration ttl = Duration.ofDays(retentionDays);
    ensureTtlIndex(mongoTemplate, Message.class, MESSAGES_TTL_INDEX, ttl);
    ensureTtlIndex(mongoTemplate, Conversation.class, CONVERSATIONS_TTL_INDEX, ttl);
    log.info("Chat retention TTL set to {} day(s) on conversations and messages", retentionDays);
  }

  private void ensureTtlIndex(
      MongoTemplate mongoTemplate, Class<?> entity, String indexName, Duration ttl) {
    dropIfExists(mongoTemplate, entity, indexName);
    mongoTemplate
        .indexOps(entity)
        .ensureIndex(new Index().on("createdAt", Sort.Direction.ASC).expire(ttl).named(indexName));
  }

  private void dropIfExists(MongoTemplate mongoTemplate, Class<?> entity, String indexName) {
    try {
      boolean present =
          mongoTemplate.indexOps(entity).getIndexInfo().stream()
              .anyMatch(info -> indexName.equals(info.getName()));
      if (present) {
        mongoTemplate.indexOps(entity).dropIndex(indexName);
        log.info("Dropped redundant Mongo index {} on {}", indexName, entity.getSimpleName());
      }
    } catch (RuntimeException e) {
      log.warn(
          "Could not drop Mongo index {} on {}: {}",
          indexName,
          entity.getSimpleName(),
          e.getMessage());
    }
  }
}
