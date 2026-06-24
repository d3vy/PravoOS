package com.pravoos.ai.config;

import com.pravoos.ai.model.mongo.Conversation;
import com.pravoos.ai.model.mongo.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@Configuration
public class MongoIndexConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexConfig.class);

    @Bean
    public ApplicationRunner mongoIndexInitializer(MongoTemplate mongoTemplate) {
        return args -> {
            mongoTemplate.indexOps(Message.class).ensureIndex(
                    new Index()
                            .on("conversationId", Sort.Direction.ASC)
                            .on("createdAt", Sort.Direction.ASC)
                            .named("messages_conversation_created"));

            mongoTemplate.indexOps(Conversation.class).ensureIndex(
                    new Index()
                            .on("lawyerId", Sort.Direction.ASC)
                            .on("createdAt", Sort.Direction.DESC)
                            .named("conversations_lawyer_created"));

            dropIfExists(mongoTemplate, Message.class, "conversationId_1");
            dropIfExists(mongoTemplate, Conversation.class, "lawyerId_1");

            log.info("MongoDB indexes ensured (messages, conversations)");
        };
    }

    private void dropIfExists(MongoTemplate mongoTemplate, Class<?> entity, String indexName) {
        try {
            boolean present = mongoTemplate.indexOps(entity).getIndexInfo().stream()
                    .anyMatch(info -> indexName.equals(info.getName()));
            if (present) {
                mongoTemplate.indexOps(entity).dropIndex(indexName);
                log.info("Dropped redundant Mongo index {} on {}", indexName, entity.getSimpleName());
            }
        } catch (RuntimeException e) {
            log.warn("Could not drop Mongo index {} on {}: {}", indexName, entity.getSimpleName(), e.getMessage());
        }
    }
}
