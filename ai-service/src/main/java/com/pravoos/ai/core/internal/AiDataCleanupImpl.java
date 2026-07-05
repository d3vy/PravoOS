package com.pravoos.ai.core.internal;

import com.pravoos.ai.core.api.AiDataCleanup;
import com.pravoos.ai.core.internal.model.mongo.Conversation;
import com.pravoos.ai.core.internal.repository.mongo.ConversationRepository;
import com.pravoos.ai.core.internal.repository.mongo.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AiDataCleanupImpl implements AiDataCleanup {

    private static final Logger log = LoggerFactory.getLogger(AiDataCleanupImpl.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public AiDataCleanupImpl(ConversationRepository conversationRepository,
                             MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    public void purgeChatData(UUID lawyerId) {
        List<String> conversationIds = conversationRepository.findByLawyerId(lawyerId)
                .stream()
                .map(Conversation::getId)
                .toList();
        if (!conversationIds.isEmpty()) {
            messageRepository.deleteByConversationIdIn(conversationIds);
        }
        conversationRepository.deleteByLawyerId(lawyerId);
        log.info("Deleted {} conversations and their messages for lawyer {}", conversationIds.size(), lawyerId);
    }
}
