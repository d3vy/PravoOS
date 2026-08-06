package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.document.api.DocumentCommand;
import com.pravoos.ai.document.api.DocumentQuery;
import com.pravoos.ai.document.api.DocumentResponse;
import com.pravoos.ai.document.api.DocumentUploadResponse;
import com.pravoos.ai.practice.internal.dto.CaseHearingEventResponse;
import com.pravoos.ai.practice.internal.dto.CaseResponse;
import com.pravoos.ai.practice.internal.dto.CreateCaseRequest;
import com.pravoos.ai.practice.internal.dto.UpdateCaseRequest;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.CaseHearingEvent;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.repository.jpa.CaseHearingEventRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CasePartyRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.practice.internal.repository.jpa.SignatureRequestRepository;
import com.pravoos.ai.shared.client.UserServiceClient;
import com.pravoos.ai.shared.exception.CaseNotFoundException;
import com.pravoos.ai.shared.exception.ClientNotFoundException;
import com.pravoos.ai.shared.exception.OrganizationAccessException;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import com.pravoos.ai.shared.model.enums.CourtSystem;
import com.pravoos.ai.shared.model.enums.DeadlineType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class CaseServiceRemainingTest {

  @Mock private CaseRepository caseRepository;
  @Mock private ClientRepository clientRepository;
  @Mock private DocumentCommand documentCommand;
  @Mock private DocumentQuery documentQuery;
  @Mock private CaseHearingEventRepository hearingEventRepository;
  @Mock private CasePartyRepository casePartyRepository;
  @Mock private SignatureRequestRepository signatureRequestRepository;
  @Mock private CourtSyncService courtSyncService;
  @Mock private UserServiceClient userServiceClient;

  private CaseService caseService() {
    return new CaseService(
        caseRepository,
        clientRepository,
        documentCommand,
        documentQuery,
        hearingEventRepository,
        casePartyRepository,
        signatureRequestRepository,
        courtSyncService,
        userServiceClient);
  }

  private Client clientWithId(UUID id, UUID lawyerId, String name) {
    Client client = new Client();
    ReflectionTestUtils.setField(client, "id", id);
    client.setLawyerId(lawyerId);
    client.setName(name);
    return client;
  }

  private Case caseWithId(UUID id, UUID lawyerId, UUID orgId) {
    Case caseEntity = new Case();
    ReflectionTestUtils.setField(caseEntity, "id", id);
    caseEntity.setLawyerId(lawyerId);
    caseEntity.setOrgId(orgId);
    caseEntity.setTitle("Дело");
    return caseEntity;
  }

  // --- create ---

  @Test
  void createTrimsTitleAndSavesWithoutClient() {
    UUID lawyerId = UUID.randomUUID();
    when(caseRepository.save(any(Case.class)))
        .thenAnswer(
            invocation -> {
              Case saved = invocation.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
              return saved;
            });

    CreateCaseRequest request =
        new CreateCaseRequest(
            "  Иск о взыскании  ", "Описание", null, null, null, null, null, null, null, null);

    CaseResponse response = caseService().create(request, lawyerId, List.of());

    assertThat(response.title()).isEqualTo("Иск о взыскании");
    assertThat(response.clientId()).isNull();
    assertThat(response.clientName()).isNull();
    assertThat(response.ownerId()).isEqualTo(lawyerId);
  }

  @Test
  void createResolvesOwnedClientAndStampsAllowedOrg() {
    UUID lawyerId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    when(clientRepository.findById(clientId))
        .thenReturn(Optional.of(clientWithId(clientId, lawyerId, "Клиент")));
    when(caseRepository.save(any(Case.class)))
        .thenAnswer(
            invocation -> {
              Case saved = invocation.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
              return saved;
            });

    CreateCaseRequest request =
        new CreateCaseRequest("Иск", null, clientId, orgId, null, null, null, null, null, null);

    CaseResponse response = caseService().create(request, lawyerId, List.of(orgId));

    assertThat(response.clientId()).isEqualTo(clientId);
    assertThat(response.clientName()).isEqualTo("Клиент");
    assertThat(response.orgId()).isEqualTo(orgId);
  }

  @Test
  void createThrowsWhenClientNotFoundOrOwnedByAnotherLawyer() {
    UUID lawyerId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    when(clientRepository.findById(clientId)).thenReturn(Optional.empty());

    CreateCaseRequest request =
        new CreateCaseRequest("Иск", null, clientId, null, null, null, null, null, null, null);

    assertThatThrownBy(() -> caseService().create(request, lawyerId, List.of()))
        .isInstanceOf(ClientNotFoundException.class);
  }

  @Test
  void createThrowsWhenOrgNotInCallerMemberships() {
    UUID lawyerId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    CreateCaseRequest request =
        new CreateCaseRequest("Иск", null, null, orgId, null, null, null, null, null, null);

    assertThatThrownBy(() -> caseService().create(request, lawyerId, List.of()))
        .isInstanceOf(OrganizationAccessException.class);
  }

  @Test
  void createDetectsCourtSystemFromNumberWhenNotExplicit() {
    UUID lawyerId = UUID.randomUUID();
    when(caseRepository.save(any(Case.class)))
        .thenAnswer(
            invocation -> {
              Case saved = invocation.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
              return saved;
            });

    CreateCaseRequest request =
        new CreateCaseRequest(
            "Иск", null, null, null, null, null, null, "А40-12345/2026", null, null);

    CaseResponse response = caseService().create(request, lawyerId, List.of());

    assertThat(response.courtSystem()).isEqualTo(CourtSystem.ARBITR);
    assertThat(response.courtCaseNumber()).isEqualTo("А40-12345/2026");
  }

  // --- update ---

  @Test
  void updateChangesFieldsOnVisibleCase() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    Case existing = caseWithId(caseId, lawyerId, null);
    when(caseRepository.findById(caseId)).thenReturn(Optional.of(existing));

    UpdateCaseRequest request =
        new UpdateCaseRequest(
            "  Новое название  ",
            "Новое описание",
            null,
            LocalDate.of(2026, 3, 1),
            null,
            null,
            null,
            null,
            BigDecimal.valueOf(5000));

    CaseResponse response = caseService().update(caseId, request, lawyerId, List.of());

    assertThat(response.title()).isEqualTo("Новое название");
    assertThat(response.description()).isEqualTo("Новое описание");
    assertThat(response.filingDeadline()).isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(response.defaultHourlyRate()).isEqualByComparingTo(BigDecimal.valueOf(5000));
  }

  @Test
  void updateThrowsWhenCaseNotVisible() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    Case foreign = caseWithId(caseId, UUID.randomUUID(), null);
    when(caseRepository.findById(caseId)).thenReturn(Optional.of(foreign));

    UpdateCaseRequest request =
        new UpdateCaseRequest("Название", null, null, null, null, null, null, null, null);

    assertThatThrownBy(() -> caseService().update(caseId, request, lawyerId, List.of()))
        .isInstanceOf(CaseNotFoundException.class);
  }

  @Test
  void updateClearsCourtPartiesWhenCourtNumberChanges() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    Case existing = caseWithId(caseId, lawyerId, null);
    existing.setCourtCaseNumber("А40-1/2025");
    existing.setCourtCaseGuid("guid-1");
    existing.setJudgeName("Судья Иванов");
    when(caseRepository.findById(caseId)).thenReturn(Optional.of(existing));

    UpdateCaseRequest request =
        new UpdateCaseRequest("Название", null, null, null, null, null, "А40-2/2025", null, null);

    caseService().update(caseId, request, lawyerId, List.of());

    assertThat(existing.getCourtCaseGuid()).isNull();
    assertThat(existing.getJudgeName()).isNull();
    verify(casePartyRepository).deleteByCaseId(caseId);
  }

  @Test
  void updateKeepsCourtPartiesWhenCourtNumberUnchanged() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    Case existing = caseWithId(caseId, lawyerId, null);
    existing.setCourtCaseNumber("А40-1/2025");
    existing.setCourtCaseGuid("guid-1");
    when(caseRepository.findById(caseId)).thenReturn(Optional.of(existing));

    UpdateCaseRequest request =
        new UpdateCaseRequest("Название", null, null, null, null, null, "А40-1/2025", null, null);

    caseService().update(caseId, request, lawyerId, List.of());

    assertThat(existing.getCourtCaseGuid()).isEqualTo("guid-1");
    verify(casePartyRepository, never()).deleteByCaseId(any());
  }

  // --- findHearingEvents / syncCourt ---

  @Test
  void findHearingEventsRequiresVisibilityAndMapsEvents() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    Case visible = caseWithId(caseId, lawyerId, null);
    when(caseRepository.findById(caseId)).thenReturn(Optional.of(visible));
    CaseHearingEvent hearing =
        new CaseHearingEvent(caseId, "src", LocalDate.of(2026, 1, 1), "Заседание", null, "Суд");
    ReflectionTestUtils.setField(hearing, "id", UUID.randomUUID());
    when(hearingEventRepository.findByCaseIdOrderByEventDateDescCreatedAtDesc(caseId))
        .thenReturn(List.of(hearing));

    List<CaseHearingEventResponse> result =
        caseService().findHearingEvents(caseId, lawyerId, List.of());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).eventType()).isEqualTo("Заседание");
  }

  @Test
  void findHearingEventsThrowsWhenNotVisible() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    when(caseRepository.findById(caseId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> caseService().findHearingEvents(caseId, lawyerId, List.of()))
        .isInstanceOf(CaseNotFoundException.class);
  }

  @Test
  void syncCourtChecksVisibilityDelegatesSyncAndReturnsEvents() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    Case visible = caseWithId(caseId, lawyerId, null);
    when(caseRepository.findById(caseId)).thenReturn(Optional.of(visible));
    when(hearingEventRepository.findByCaseIdOrderByEventDateDescCreatedAtDesc(caseId))
        .thenReturn(List.of());

    List<CaseHearingEventResponse> result = caseService().syncCourt(caseId, lawyerId, List.of());

    verify(courtSyncService).syncCase(caseId);
    assertThat(result).isEmpty();
  }

  // --- findByLawyer ---

  @Test
  void findByLawyerRejectsOrgFilterNotInCallerMemberships() {
    UUID lawyerId = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                caseService()
                    .findByLawyer(lawyerId, List.of(), null, UUID.randomUUID(), null, 0, 20))
        .isInstanceOf(OrganizationAccessException.class);
  }

  @Test
  void findByLawyerMapsClientNamesForResults() {
    UUID lawyerId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    Case caseEntity = caseWithId(caseId, lawyerId, null);
    caseEntity.setClientId(clientId);
    when(caseRepository.findVisible(
            any(), anyCollection(), any(), any(), any(), anyCollection(), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(caseEntity)));
    when(clientRepository.findAllById(anyCollection()))
        .thenReturn(List.of(clientWithId(clientId, lawyerId, "Клиент Х")));

    Page<CaseResponse> result =
        caseService().findByLawyer(lawyerId, List.of(), null, null, null, 0, 20);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).clientName()).isEqualTo("Клиент Х");
  }

  // --- updateStatus ---

  @Test
  void updateStatusChangesStatusOnVisibleCase() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    Case visible = caseWithId(caseId, lawyerId, null);
    when(caseRepository.findById(caseId)).thenReturn(Optional.of(visible));

    CaseResponse response =
        caseService().updateStatus(caseId, CaseStatus.CLOSED_WON, lawyerId, List.of());

    assertThat(response.status()).isEqualTo(CaseStatus.CLOSED_WON);
    assertThat(visible.getStatus()).isEqualTo(CaseStatus.CLOSED_WON);
  }

  // --- get / delete ---

  @Test
  void getReturnsCaseResponseWithResolvedClientName() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    Case visible = caseWithId(caseId, lawyerId, null);
    visible.setClientId(clientId);
    when(caseRepository.findById(caseId)).thenReturn(Optional.of(visible));
    when(clientRepository.findById(clientId))
        .thenReturn(Optional.of(clientWithId(clientId, lawyerId, "Клиент")));

    CaseResponse response = caseService().get(caseId, lawyerId, List.of());

    assertThat(response.clientName()).isEqualTo("Клиент");
  }

  @Test
  void getReturnsNullClientNameWhenClientMissing() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    Case visible = caseWithId(caseId, lawyerId, null);
    visible.setClientId(clientId);
    when(caseRepository.findById(caseId)).thenReturn(Optional.of(visible));
    when(clientRepository.findById(clientId)).thenReturn(Optional.empty());

    CaseResponse response = caseService().get(caseId, lawyerId, List.of());

    assertThat(response.clientName()).isNull();
  }

  @Test
  void deleteRemovesOwnedCaseAndCascadesRelatedData() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    Case owned = caseWithId(caseId, lawyerId, null);
    when(caseRepository.findById(caseId)).thenReturn(Optional.of(owned));

    caseService().delete(caseId, lawyerId);

    verify(signatureRequestRepository).deleteByCaseId(caseId);
    verify(documentCommand).deleteByCase(caseId);
    verify(casePartyRepository).deleteByCaseId(caseId);
    verify(caseRepository).delete(owned);
  }

  @Test
  void deleteThrowsWhenNotOwnedAndDoesNotCascadeAnything() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    Case foreign = caseWithId(caseId, UUID.randomUUID(), null);
    when(caseRepository.findById(caseId)).thenReturn(Optional.of(foreign));

    assertThatThrownBy(() -> caseService().delete(caseId, lawyerId))
        .isInstanceOf(CaseNotFoundException.class);
    verify(signatureRequestRepository, never()).deleteByCaseId(any());
    verify(documentCommand, never()).deleteByCase(any());
    verify(casePartyRepository, never()).deleteByCaseId(any());
    verify(caseRepository, never()).delete(any());
  }

  // --- documents ---

  @Test
  void uploadDocumentRequiresVisibilityAndDelegates() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    Case visible = caseWithId(caseId, lawyerId, null);
    when(caseRepository.findById(caseId)).thenReturn(Optional.of(visible));
    MultipartFile file = mock(MultipartFile.class);
    DocumentUploadResponse expected = mock(DocumentUploadResponse.class);
    when(documentCommand.upload(file, "Заголовок", lawyerId, caseId)).thenReturn(expected);

    DocumentUploadResponse result =
        caseService().uploadDocument(caseId, file, "Заголовок", lawyerId, List.of());

    assertThat(result).isSameAs(expected);
  }

  @Test
  void uploadDocumentThrowsWhenCaseNotVisible() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    when(caseRepository.findById(caseId)).thenReturn(Optional.empty());
    MultipartFile file = mock(MultipartFile.class);

    assertThatThrownBy(
            () -> caseService().uploadDocument(caseId, file, "Заголовок", lawyerId, List.of()))
        .isInstanceOf(CaseNotFoundException.class);
  }

  @Test
  void findDocumentsRequiresVisibilityAndDelegates() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    Case visible = caseWithId(caseId, lawyerId, null);
    when(caseRepository.findById(caseId)).thenReturn(Optional.of(visible));
    DocumentResponse doc = mock(DocumentResponse.class);
    when(documentQuery.findByCase(caseId)).thenReturn(List.of(doc));

    List<DocumentResponse> result = caseService().findDocuments(caseId, lawyerId, List.of());

    assertThat(result).containsExactly(doc);
  }

  @Test
  void setDocumentVisibilityRequiresVisibilityAndDelegates() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Case visible = caseWithId(caseId, lawyerId, null);
    when(caseRepository.findById(caseId)).thenReturn(Optional.of(visible));
    DocumentResponse expected = mock(DocumentResponse.class);
    when(documentCommand.setClientVisibility(documentId, caseId, true)).thenReturn(expected);

    DocumentResponse result =
        caseService().setDocumentVisibility(caseId, documentId, true, lawyerId, List.of());

    assertThat(result).isSameAs(expected);
  }

  // --- setDeadlineIfAbsent ---

  @Test
  void setDeadlineIfAbsentSetsFilingDeadlineWhenAbsent() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    Case visible = caseWithId(caseId, lawyerId, null);
    when(caseRepository.findById(caseId)).thenReturn(Optional.of(visible));

    boolean applied =
        caseService()
            .setDeadlineIfAbsent(
                caseId,
                DeadlineType.FILING_DEADLINE,
                LocalDate.of(2026, 5, 1),
                lawyerId,
                List.of());

    assertThat(applied).isTrue();
    assertThat(visible.getFilingDeadline()).isEqualTo(LocalDate.of(2026, 5, 1));
  }

  @Test
  void setDeadlineIfAbsentDoesNotOverwriteExistingDeadline() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    Case visible = caseWithId(caseId, lawyerId, null);
    visible.setNextHearingDate(LocalDate.of(2026, 1, 1));
    when(caseRepository.findById(caseId)).thenReturn(Optional.of(visible));

    boolean applied =
        caseService()
            .setDeadlineIfAbsent(
                caseId, DeadlineType.NEXT_HEARING, LocalDate.of(2026, 5, 1), lawyerId, List.of());

    assertThat(applied).isFalse();
    assertThat(visible.getNextHearingDate()).isEqualTo(LocalDate.of(2026, 1, 1));
  }

  @Test
  void setDeadlineIfAbsentSetsExpiryWhenAbsent() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    Case visible = caseWithId(caseId, lawyerId, null);
    when(caseRepository.findById(caseId)).thenReturn(Optional.of(visible));

    boolean applied =
        caseService()
            .setDeadlineIfAbsent(
                caseId, DeadlineType.EXPIRY, LocalDate.of(2026, 6, 1), lawyerId, List.of());

    assertThat(applied).isTrue();
    assertThat(visible.getExpiresAt()).isEqualTo(LocalDate.of(2026, 6, 1));
  }

  @Test
  void setDeadlineIfAbsentThrowsForTaskType() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    Case visible = caseWithId(caseId, lawyerId, null);
    when(caseRepository.findById(caseId)).thenReturn(Optional.of(visible));

    assertThatThrownBy(
            () ->
                caseService()
                    .setDeadlineIfAbsent(
                        caseId, DeadlineType.TASK, LocalDate.now(), lawyerId, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
