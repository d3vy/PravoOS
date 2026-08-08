package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.api.ConversationSearchQuery;
import com.pravoos.ai.core.api.ConversationSearchQuery.ConversationSearchHit;
import com.pravoos.ai.document.api.DocumentSearchQuery;
import com.pravoos.ai.document.api.DocumentSearchQuery.DocumentSearchHit;
import com.pravoos.ai.practice.internal.dto.GlobalSearchResponse;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.practice.internal.repository.jpa.InvoiceRepository;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

  @Mock private CaseRepository caseRepository;
  @Mock private ClientRepository clientRepository;
  @Mock private InvoiceRepository invoiceRepository;
  @Mock private DocumentSearchQuery documentSearchQuery;
  @Mock private ConversationSearchQuery conversationSearchQuery;

  @InjectMocks private SearchService searchService;

  private final UUID lawyerId = UUID.randomUUID();

  private Client clientWithId(UUID id, String name, String email, String phone) {
    Client client = new Client();
    ReflectionTestUtils.setField(client, "id", id);
    client.setName(name);
    client.setEmail(email);
    client.setPhone(phone);
    return client;
  }

  private Case caseWithId(UUID id, String title, CaseStatus status, UUID clientId) {
    Case caseEntity = new Case();
    ReflectionTestUtils.setField(caseEntity, "id", id);
    caseEntity.setTitle(title);
    caseEntity.setStatus(status);
    caseEntity.setClientId(clientId);
    return caseEntity;
  }

  private Invoice invoiceWithId(
      UUID id,
      String number,
      UUID clientId,
      BigDecimal total,
      String currency,
      InvoiceStatus status) {
    Invoice invoice = new Invoice();
    ReflectionTestUtils.setField(invoice, "id", id);
    invoice.setNumber(number);
    invoice.setClientId(clientId);
    invoice.setTotal(total);
    invoice.setCurrency(currency);
    invoice.setStatus(status);
    return invoice;
  }

  @Test
  void searchReturnsAllEmptyWhenQueryIsBlank() {
    GlobalSearchResponse result = searchService.search(lawyerId, "   ", false);

    assertThat(result.cases()).isEmpty();
    assertThat(result.conversations()).isEmpty();
    assertThat(result.documents()).isEmpty();
    assertThat(result.clients()).isEmpty();
    assertThat(result.invoices()).isEmpty();
    verify(caseRepository, never()).search(any(), any(), anyString(), anyCollection(), any());
    verify(clientRepository, never()).findByLawyerIdOrderByCreatedAtDesc(any(UUID.class));
  }

  @Test
  void searchReturnsAllEmptyWhenQueryIsNull() {
    GlobalSearchResponse result = searchService.search(lawyerId, null, false);

    assertThat(result.cases()).isEmpty();
    assertThat(result.conversations()).isEmpty();
    assertThat(result.documents()).isEmpty();
    assertThat(result.clients()).isEmpty();
    assertThat(result.invoices()).isEmpty();
  }

  @Test
  void searchMapsCasesWithClientNameAndTrimsQuery() {
    UUID clientId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    Client client = clientWithId(clientId, "Иван Иванов", "ivan@example.com", "+7900");
    when(clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)).thenReturn(List.of(client));
    when(caseRepository.search(eq(lawyerId), eq(null), anyString(), anyCollection(), any()))
        .thenReturn(
            new PageImpl<>(
                List.of(caseWithId(caseId, "Дело Иванова", CaseStatus.IN_PROGRESS, clientId))));
    when(conversationSearchQuery.searchConversations(any(), anyString(), anyInt()))
        .thenReturn(List.of());
    when(documentSearchQuery.searchDocuments(
            any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean(), anyInt()))
        .thenReturn(List.of());
    when(invoiceRepository.search(any(), anyString(), anyCollection(), any()))
        .thenReturn(List.of());

    GlobalSearchResponse result = searchService.search(lawyerId, "  Иванов  ", false);

    assertThat(result.cases()).hasSize(1);
    var hit = result.cases().get(0);
    assertThat(hit.id()).isEqualTo(caseId);
    assertThat(hit.title()).isEqualTo("Дело Иванова");
    assertThat(hit.status()).isEqualTo(CaseStatus.IN_PROGRESS);
    assertThat(hit.statusName()).isEqualTo(CaseStatus.IN_PROGRESS.getDisplayName());
    assertThat(hit.clientName()).isEqualTo("Иван Иванов");

    verify(caseRepository).search(eq(lawyerId), eq(null), anyString(), anyCollection(), any());
  }

  @Test
  void searchCaseHitHasNullClientNameWhenClientIdIsNull() {
    UUID caseId = UUID.randomUUID();
    when(clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)).thenReturn(List.of());
    when(caseRepository.search(eq(lawyerId), eq(null), anyString(), anyCollection(), any()))
        .thenReturn(
            new PageImpl<>(
                List.of(caseWithId(caseId, "Дело без клиента", CaseStatus.INTAKE, null))));
    when(conversationSearchQuery.searchConversations(any(), anyString(), anyInt()))
        .thenReturn(List.of());
    when(documentSearchQuery.searchDocuments(
            any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean(), anyInt()))
        .thenReturn(List.of());
    when(invoiceRepository.search(any(), anyString(), anyCollection(), any()))
        .thenReturn(List.of());

    GlobalSearchResponse result = searchService.search(lawyerId, "дело", false);

    assertThat(result.cases()).hasSize(1);
    assertThat(result.cases().get(0).clientName()).isNull();
  }

  @Test
  void searchMapsConversationsAndDocuments() {
    when(clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)).thenReturn(List.of());
    when(caseRepository.search(any(), any(), anyString(), anyCollection(), any()))
        .thenReturn(Page.empty());
    when(conversationSearchQuery.searchConversations(eq(lawyerId), eq("вопрос"), eq(10)))
        .thenReturn(List.of(new ConversationSearchHit("conv-1", "Обсуждение договора")));
    UUID docId = UUID.randomUUID();
    UUID docCaseId = UUID.randomUUID();
    when(documentSearchQuery.searchDocuments(eq(lawyerId), eq("вопрос"), eq(true), eq(10)))
        .thenReturn(
            List.of(
                new DocumentSearchHit(docId, "Договор.pdf", "dogovor.pdf", docCaseId, "снипет")));
    when(invoiceRepository.search(any(), anyString(), anyCollection(), any()))
        .thenReturn(List.of());

    GlobalSearchResponse result = searchService.search(lawyerId, "вопрос", true);

    assertThat(result.conversations()).hasSize(1);
    assertThat(result.conversations().get(0).id()).isEqualTo("conv-1");
    assertThat(result.conversations().get(0).title()).isEqualTo("Обсуждение договора");

    assertThat(result.documents()).hasSize(1);
    var docHit = result.documents().get(0);
    assertThat(docHit.id()).isEqualTo(docId);
    assertThat(docHit.title()).isEqualTo("Договор.pdf");
    assertThat(docHit.fileName()).isEqualTo("dogovor.pdf");
    assertThat(docHit.caseId()).isEqualTo(docCaseId);
    assertThat(docHit.snippet()).isEqualTo("снипет");

    verify(documentSearchQuery).searchDocuments(eq(lawyerId), eq("вопрос"), eq(true), eq(10));
  }

  @Test
  void searchClientsFiltersByNameEmailOrPhoneCaseInsensitively() {
    UUID matchByName = UUID.randomUUID();
    UUID matchByEmail = UUID.randomUUID();
    UUID matchByPhone = UUID.randomUUID();
    UUID noMatch = UUID.randomUUID();
    List<Client> clients =
        List.of(
            clientWithId(matchByName, "ООО Petrov Group", "a@x.com", "+70001"),
            clientWithId(matchByEmail, "Сидоров", "PETROV@x.com", "+70002"),
            clientWithId(matchByPhone, "Кузнецов", "b@x.com", "8-900-PETROV-00"),
            clientWithId(noMatch, "Николаев", "c@x.com", "+70003"));
    when(clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)).thenReturn(clients);
    when(caseRepository.search(any(), any(), anyString(), anyCollection(), any()))
        .thenReturn(Page.empty());
    when(conversationSearchQuery.searchConversations(any(), anyString(), anyInt()))
        .thenReturn(List.of());
    when(documentSearchQuery.searchDocuments(
            any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean(), anyInt()))
        .thenReturn(List.of());
    when(invoiceRepository.search(any(), anyString(), anyCollection(), any()))
        .thenReturn(List.of());

    GlobalSearchResponse result = searchService.search(lawyerId, "petrov", false);

    assertThat(result.clients())
        .extracting(GlobalSearchResponse.ClientHit::id)
        .containsExactlyInAnyOrder(matchByName, matchByEmail, matchByPhone);
  }

  @Test
  void searchInvoicesFiltersByNumberOrClientName() {
    UUID clientId = UUID.randomUUID();
    Client client = clientWithId(clientId, "ООО Ромашка", "info@romashka.ru", null);
    when(clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)).thenReturn(List.of(client));
    when(caseRepository.search(any(), any(), anyString(), anyCollection(), any()))
        .thenReturn(Page.empty());
    when(conversationSearchQuery.searchConversations(any(), anyString(), anyInt()))
        .thenReturn(List.of());
    when(documentSearchQuery.searchDocuments(
            any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean(), anyInt()))
        .thenReturn(List.of());

    UUID matchingInvoiceId = UUID.randomUUID();
    Invoice matchingByNumber =
        invoiceWithId(
            matchingInvoiceId,
            "INV-ROMASHKA-1",
            clientId,
            BigDecimal.TEN,
            "RUB",
            InvoiceStatus.ISSUED);
    when(invoiceRepository.search(eq(lawyerId), anyString(), anyCollection(), any()))
        .thenReturn(List.of(matchingByNumber));

    GlobalSearchResponse result = searchService.search(lawyerId, "romashka", false);

    assertThat(result.invoices()).hasSize(1);
    var hit = result.invoices().get(0);
    assertThat(hit.id()).isEqualTo(matchingInvoiceId);
    assertThat(hit.number()).isEqualTo("INV-ROMASHKA-1");
    assertThat(hit.clientName()).isEqualTo("ООО Ромашка");
    assertThat(hit.total()).isEqualByComparingTo(BigDecimal.TEN);
    assertThat(hit.currency()).isEqualTo("RUB");
    assertThat(hit.status()).isEqualTo(InvoiceStatus.ISSUED);
    assertThat(hit.statusName()).isEqualTo(InvoiceStatus.ISSUED.getDisplayName());
  }

  @Test
  void searchPushesNumberPatternAndClientMatchesIntoTheInvoiceQuery() {
    UUID clientId = UUID.randomUUID();
    when(clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId))
        .thenReturn(List.of(clientWithId(clientId, "ООО Ромашка", null, null)));
    when(caseRepository.search(any(), any(), anyString(), anyCollection(), any()))
        .thenReturn(Page.empty());
    when(conversationSearchQuery.searchConversations(any(), anyString(), anyInt()))
        .thenReturn(List.of());
    when(documentSearchQuery.searchDocuments(
            any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean(), anyInt()))
        .thenReturn(List.of());
    when(invoiceRepository.search(any(), anyString(), anyCollection(), any()))
        .thenReturn(List.of());

    searchService.search(lawyerId, "Ромашка", false);

    ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Collection<UUID>> clientIds = ArgumentCaptor.forClass(Collection.class);
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(invoiceRepository)
        .search(eq(lawyerId), pattern.capture(), clientIds.capture(), pageable.capture());

    assertThat(pattern.getValue()).isEqualTo("%ромашка%");
    assertThat(clientIds.getValue()).containsExactly(clientId);
    assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
  }

  @Test
  void searchLoadsTheClientsOfTheLawyerOnlyOnce() {
    when(clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId))
        .thenReturn(List.of(clientWithId(UUID.randomUUID(), "ООО Ромашка", null, null)));
    when(caseRepository.search(any(), any(), anyString(), anyCollection(), any()))
        .thenReturn(Page.empty());
    when(conversationSearchQuery.searchConversations(any(), anyString(), anyInt()))
        .thenReturn(List.of());
    when(documentSearchQuery.searchDocuments(
            any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean(), anyInt()))
        .thenReturn(List.of());
    when(invoiceRepository.search(any(), anyString(), anyCollection(), any()))
        .thenReturn(List.of());

    searchService.search(lawyerId, "ромашка", false);

    verify(clientRepository, times(1)).findByLawyerIdOrderByCreatedAtDesc(lawyerId);
  }

  @Test
  void searchLimitsEachSourceToMaxHitsPerSource() {
    List<Client> manyClients =
        java.util.stream.IntStream.range(0, 15)
            .mapToObj(i -> clientWithId(UUID.randomUUID(), "Клиент совпадение " + i, null, null))
            .toList();
    when(clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)).thenReturn(manyClients);
    when(caseRepository.search(any(), any(), anyString(), anyCollection(), any()))
        .thenReturn(Page.empty());
    when(conversationSearchQuery.searchConversations(any(), anyString(), anyInt()))
        .thenReturn(List.of());
    when(documentSearchQuery.searchDocuments(
            any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean(), anyInt()))
        .thenReturn(List.of());
    when(invoiceRepository.search(any(), anyString(), anyCollection(), any()))
        .thenReturn(List.of());

    GlobalSearchResponse result = searchService.search(lawyerId, "совпадение", false);

    assertThat(result.clients()).hasSize(10);
  }
}
