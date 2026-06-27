package com.pravoos.ai.service;

import com.pravoos.ai.model.entity.PendingLawyerPurge;
import com.pravoos.ai.model.mongo.Conversation;
import com.pravoos.ai.repository.jpa.CaseDraftRepository;
import com.pravoos.ai.repository.jpa.CaseRepository;
import com.pravoos.ai.repository.jpa.CaseTaskRepository;
import com.pravoos.ai.repository.jpa.ClientContactRepository;
import com.pravoos.ai.repository.jpa.ClientRepository;
import com.pravoos.ai.repository.jpa.DocumentTemplateRepository;
import com.pravoos.ai.repository.jpa.PendingLawyerPurgeRepository;
import com.pravoos.ai.repository.mongo.ConversationRepository;
import com.pravoos.ai.repository.mongo.MessageRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LawyerDataCleanupService {

    private static final Logger log = LoggerFactory.getLogger(LawyerDataCleanupService.class);
    private static final int RETRY_BATCH_SIZE = 50;

    private final CaseRepository caseRepository;
    private final CaseTaskRepository caseTaskRepository;
    private final CaseDraftRepository caseDraftRepository;
    private final ClientRepository clientRepository;
    private final ClientContactRepository clientContactRepository;
    private final DocumentTemplateRepository documentTemplateRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final PendingLawyerPurgeRepository pendingLawyerPurgeRepository;
    private final LawyerDataCleanupService self;

    public LawyerDataCleanupService(CaseRepository caseRepository,
                                    CaseTaskRepository caseTaskRepository,
                                    CaseDraftRepository caseDraftRepository,
                                    ClientRepository clientRepository,
                                    ClientContactRepository clientContactRepository,
                                    DocumentTemplateRepository documentTemplateRepository,
                                    ConversationRepository conversationRepository,
                                    MessageRepository messageRepository,
                                    PendingLawyerPurgeRepository pendingLawyerPurgeRepository,
                                    @Lazy LawyerDataCleanupService self) {
        this.caseRepository = caseRepository;
        this.caseTaskRepository = caseTaskRepository;
        this.caseDraftRepository = caseDraftRepository;
        this.clientRepository = clientRepository;
        this.clientContactRepository = clientContactRepository;
        this.documentTemplateRepository = documentTemplateRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.pendingLawyerPurgeRepository = pendingLawyerPurgeRepository;
        this.self = self;
    }

    public void purgeLawyerData(UUID lawyerId) {
        self.recordPurgeIntent(lawyerId);
        attemptPurge(lawyerId);
    }

    @Transactional
    public void recordPurgeIntent(UUID lawyerId) {
        if (!pendingLawyerPurgeRepository.existsById(lawyerId)) {
            pendingLawyerPurgeRepository.save(new PendingLawyerPurge(lawyerId));
        }
    }

    @Scheduled(fixedDelayString = "${app.lawyer-purge.retry-interval-ms:300000}")
    @SchedulerLock(name = "LawyerDataCleanupService_retryPending",
            lockAtLeastFor = "PT10S", lockAtMostFor = "PT10M")
    public void retryPendingPurges() {
        List<PendingLawyerPurge> pending =
                pendingLawyerPurgeRepository.findOldestBatch(PageRequest.of(0, RETRY_BATCH_SIZE));
        for (PendingLawyerPurge entry : pending) {
            attemptPurge(entry.getLawyerId());
        }
    }

    private void attemptPurge(UUID lawyerId) {
        try {
            self.purgeRelationalData(lawyerId);
            purgeChatData(lawyerId);
            pendingLawyerPurgeRepository.deleteById(lawyerId);
            log.info("Purged AI data for deleted lawyer {}", lawyerId);
        } catch (Exception e) {
            self.markPurgeFailed(lawyerId, e.getMessage());
            log.error("Failed to purge AI data for lawyer {}, will retry later", lawyerId, e);
        }
    }

    @Transactional
    public void markPurgeFailed(UUID lawyerId, String error) {
        pendingLawyerPurgeRepository.findById(lawyerId)
                .ifPresent(entry -> entry.recordFailedAttempt(error));
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
