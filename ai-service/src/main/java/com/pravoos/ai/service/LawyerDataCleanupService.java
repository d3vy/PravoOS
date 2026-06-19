package com.pravoos.ai.service;

import com.pravoos.ai.model.mongo.Conversation;
import com.pravoos.ai.repository.jpa.CaseDraftRepository;
import com.pravoos.ai.repository.jpa.CaseRepository;
import com.pravoos.ai.repository.jpa.CaseTaskRepository;
import com.pravoos.ai.repository.jpa.ClientContactRepository;
import com.pravoos.ai.repository.jpa.ClientRepository;
import com.pravoos.ai.repository.jpa.DocumentTemplateRepository;
import com.pravoos.ai.repository.mongo.ConversationRepository;
import com.pravoos.ai.repository.mongo.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LawyerDataCleanupService {

    private static final Logger log = LoggerFactory.getLogger(LawyerDataCleanupService.class);

    private final CaseRepository caseRepository;
    private final CaseTaskRepository caseTaskRepository;
    private final CaseDraftRepository caseDraftRepository;
    private final ClientRepository clientRepository;
    private final ClientContactRepository clientContactRepository;
    private final DocumentTemplateRepository documentTemplateRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final LawyerDataCleanupService self;

    public LawyerDataCleanupService(CaseRepository caseRepository,
                                    CaseTaskRepository caseTaskRepository,
                                    CaseDraftRepository caseDraftRepository,
                                    ClientRepository clientRepository,
                                    ClientContactRepository clientContactRepository,
                                    DocumentTemplateRepository documentTemplateRepository,
                                    ConversationRepository conversationRepository,
                                    MessageRepository messageRepository,
                                    @Lazy LawyerDataCleanupService self) {
        this.caseRepository = caseRepository;
        this.caseTaskRepository = caseTaskRepository;
        this.caseDraftRepository = caseDraftRepository;
        this.clientRepository = clientRepository;
        this.clientContactRepository = clientContactRepository;
        this.documentTemplateRepository = documentTemplateRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.self = self;
    }

    public void purgeLawyerData(UUID lawyerId) {
        self.purgeRelationalData(lawyerId);
        purgeChatData(lawyerId);
        log.info("Purged AI data for deleted lawyer {}", lawyerId);
    }

    @Transactional
    public void purgeRelationalData(UUID lawyerId) {
        int tasks = caseTaskRepository.deleteByLawyerId(lawyerId);
        int drafts = caseDraftRepository.deleteByLawyerId(lawyerId);
        int cases = caseRepository.deleteByLawyerId(lawyerId);
        int contacts = clientContactRepository.deleteByLawyerId(lawyerId);
        int clients = clientRepository.deleteByLawyerId(lawyerId);
        int templates = documentTemplateRepository.deleteByLawyerId(lawyerId);
        log.info("Deleted {} tasks, {} drafts, {} cases, {} contacts, {} clients and {} templates for lawyer {}",
                tasks, drafts, cases, contacts, clients, templates, lawyerId);
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
