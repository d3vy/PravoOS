package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.core.internal.model.mongo.Conversation;
import com.pravoos.ai.practice.internal.repository.jpa.*;
import com.pravoos.ai.core.internal.repository.mongo.ConversationRepository;
import com.pravoos.ai.core.internal.repository.mongo.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LawyerDataCleanupServiceTest {

    @Mock private CaseRepository caseRepository;
    @Mock private CaseTaskRepository caseTaskRepository;
    @Mock private CaseDraftRepository caseDraftRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private ClientContactRepository clientContactRepository;
    @Mock private DocumentTemplateRepository documentTemplateRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private PendingLawyerPurgeRepository pendingLawyerPurgeRepository;

    private LawyerDataCleanupService service(LawyerDataCleanupService self) {
        return new LawyerDataCleanupService(caseRepository, caseTaskRepository, caseDraftRepository,
                clientRepository, clientContactRepository, documentTemplateRepository,
                conversationRepository, messageRepository, pendingLawyerPurgeRepository, self);
    }

    @Test
    void purgeRelationalDataDeletesEveryLawyerScopedEntity() {
        UUID lawyerId = UUID.randomUUID();

        service(null).purgeRelationalData(lawyerId);

        verify(caseTaskRepository).deleteByLawyerId(lawyerId);
        verify(caseDraftRepository).deleteByLawyerId(lawyerId);
        verify(caseRepository).deleteByLawyerId(lawyerId);
        verify(clientContactRepository).deleteByLawyerId(lawyerId);
        verify(clientRepository).deleteByLawyerId(lawyerId);
        verify(documentTemplateRepository).deleteByLawyerId(lawyerId);
        verifyNoMoreInteractions(caseTaskRepository, caseDraftRepository, caseRepository,
                clientContactRepository, clientRepository, documentTemplateRepository);
    }

    @Test
    void purgeRemovesConversationsAndTheirMessagesAndClearsPendingMarker() {
        UUID lawyerId = UUID.randomUUID();
        Conversation conversation = new Conversation(lawyerId, "title");
        ReflectionTestUtils.setField(conversation, "id", "conv-1");
        when(conversationRepository.findByLawyerId(lawyerId)).thenReturn(List.of(conversation));

        LawyerDataCleanupService self = mock(LawyerDataCleanupService.class);
        service(self).purgeLawyerData(lawyerId, Map.of());

        verify(self).reassignOrgCases(lawyerId, Map.of());
        verify(self).recordPurgeIntent(lawyerId);
        verify(self).purgeRelationalData(lawyerId);
        verify(messageRepository).deleteByConversationIdIn(List.of(conversation.getId()));
        verify(conversationRepository).deleteByLawyerId(lawyerId);
        verify(pendingLawyerPurgeRepository).deleteById(lawyerId);
    }

    @Test
    void reassignOrgCasesTransfersDraftsThenCasesToOrgOwner() {
        UUID lawyerId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        service(null).reassignOrgCases(lawyerId, Map.of(orgId, ownerId));

        InOrder order = inOrder(caseDraftRepository, caseRepository);
        order.verify(caseDraftRepository).reassignForOrgCases(lawyerId, orgId, ownerId);
        order.verify(caseRepository).reassignOrgCasesToOwner(lawyerId, orgId, ownerId);
    }

    @Test
    void reassignOrgCasesSkipsSelfOwnedOrgs() {
        UUID lawyerId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        service(null).reassignOrgCases(lawyerId, Map.of(orgId, lawyerId));

        verifyNoInteractions(caseDraftRepository, caseRepository);
    }
}
