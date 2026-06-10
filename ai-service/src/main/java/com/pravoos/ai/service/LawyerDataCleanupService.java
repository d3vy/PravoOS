package com.pravoos.ai.service;

import com.pravoos.ai.model.mongo.Conversation;
import com.pravoos.ai.repository.jpa.CaseDraftRepository;
import com.pravoos.ai.repository.jpa.CaseRepository;
import com.pravoos.ai.repository.mongo.ConversationRepository;
import com.pravoos.ai.repository.mongo.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LawyerDataCleanupService {

    private static final Logger log = LoggerFactory.getLogger(LawyerDataCleanupService.class);

    private final CaseRepository caseRepository;
    private final CaseDraftRepository caseDraftRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public LawyerDataCleanupService(CaseRepository caseRepository,
                                    CaseDraftRepository caseDraftRepository,
                                    ConversationRepository conversationRepository,
                                    MessageRepository messageRepository) {
        this.caseRepository = caseRepository;
        this.caseDraftRepository = caseDraftRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    public void purgeLawyerData(UUID lawyerId) {
        purgeRelationalData(lawyerId);
        purgeChatData(lawyerId);
        log.info("Purged AI data for deleted lawyer {}", lawyerId);
    }

    @Transactional
    public void purgeRelationalData(UUID lawyerId) {
        int drafts = caseDraftRepository.deleteByLawyerId(lawyerId);
        int cases = caseRepository.deleteByLawyerId(lawyerId);
        log.info("Deleted {} drafts and {} cases for lawyer {}", drafts, cases, lawyerId);
    }

    private void purgeChatData(UUID lawyerId) {
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
