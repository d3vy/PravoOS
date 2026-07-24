package com.pravoos.ai.practice.internal.service;

import static org.mockito.Mockito.*;

import com.pravoos.ai.core.api.AiDataCleanup;
import com.pravoos.ai.practice.internal.repository.jpa.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LawyerDataCleanupServiceTest {

  @Mock private CaseRepository caseRepository;
  @Mock private CasePartyRepository casePartyRepository;
  @Mock private CaseAnalysisRepository caseAnalysisRepository;
  @Mock private CaseTaskRepository caseTaskRepository;
  @Mock private CaseDraftRepository caseDraftRepository;
  @Mock private WorkflowRunRepository workflowRunRepository;
  @Mock private WorkflowDefinitionRepository workflowDefinitionRepository;
  @Mock private ClientRepository clientRepository;
  @Mock private ClientContactRepository clientContactRepository;
  @Mock private DocumentTemplateRepository documentTemplateRepository;
  @Mock private SavedViewRepository savedViewRepository;
  @Mock private SignatureRequestRepository signatureRequestRepository;
  @Mock private TimeEntryRepository timeEntryRepository;
  @Mock private InvoiceRepository invoiceRepository;
  @Mock private AiDataCleanup aiDataCleanup;
  @Mock private PendingLawyerPurgeRepository pendingLawyerPurgeRepository;

  private LawyerDataCleanupService service(LawyerDataCleanupService self) {
    return new LawyerDataCleanupService(
        caseRepository,
        casePartyRepository,
        caseAnalysisRepository,
        caseTaskRepository,
        caseDraftRepository,
        workflowRunRepository,
        workflowDefinitionRepository,
        clientRepository,
        clientContactRepository,
        documentTemplateRepository,
        savedViewRepository,
        signatureRequestRepository,
        timeEntryRepository,
        invoiceRepository,
        aiDataCleanup,
        pendingLawyerPurgeRepository,
        self);
  }

  @Test
  void purgeRelationalDataDeletesEveryLawyerScopedEntity() {
    UUID lawyerId = UUID.randomUUID();

    service(null).purgeRelationalData(lawyerId);

    verify(timeEntryRepository).deleteByLawyerId(lawyerId);
    verify(invoiceRepository).deleteByLawyerId(lawyerId);
    verify(workflowRunRepository).deleteByLawyerCases(lawyerId);
    verify(caseAnalysisRepository).deleteByLawyerCases(lawyerId);
    verify(caseTaskRepository).deleteByLawyerId(lawyerId);
    verify(caseDraftRepository).deleteByLawyerId(lawyerId);
    verify(casePartyRepository).deleteByLawyerId(lawyerId);
    verify(caseRepository).deleteByLawyerId(lawyerId);
    verify(workflowDefinitionRepository).deleteByCreatedByLawyer(lawyerId);
    verify(clientContactRepository).deleteByLawyerId(lawyerId);
    verify(clientRepository).deleteByLawyerId(lawyerId);
    verify(documentTemplateRepository).deleteByLawyerId(lawyerId);
    verify(signatureRequestRepository).deleteByRequestedBy(lawyerId);
    verifyNoMoreInteractions(
        timeEntryRepository,
        invoiceRepository,
        workflowRunRepository,
        caseAnalysisRepository,
        caseTaskRepository,
        caseDraftRepository,
        casePartyRepository,
        caseRepository,
        workflowDefinitionRepository,
        clientContactRepository,
        clientRepository,
        documentTemplateRepository);
  }

  @Test
  void purgeDelegatesChatCleanupAndClearsPendingMarker() {
    UUID lawyerId = UUID.randomUUID();

    LawyerDataCleanupService self = mock(LawyerDataCleanupService.class);
    service(self).purgeLawyerData(lawyerId, Map.of());

    verify(self).reassignOrgCases(lawyerId, Map.of());
    verify(self).recordPurgeIntent(lawyerId);
    verify(self).purgeRelationalData(lawyerId);
    verify(aiDataCleanup).purgeChatData(lawyerId);
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
