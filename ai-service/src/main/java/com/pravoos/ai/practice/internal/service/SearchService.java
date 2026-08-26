package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.core.api.ConversationSearchQuery;
import com.pravoos.ai.document.api.DocumentSearchQuery;
import com.pravoos.ai.practice.internal.dto.GlobalSearchResponse;
import com.pravoos.ai.practice.internal.dto.GlobalSearchResponse.CaseHit;
import com.pravoos.ai.practice.internal.dto.GlobalSearchResponse.ClientHit;
import com.pravoos.ai.practice.internal.dto.GlobalSearchResponse.ConversationHit;
import com.pravoos.ai.practice.internal.dto.GlobalSearchResponse.DocumentHit;
import com.pravoos.ai.practice.internal.dto.GlobalSearchResponse.InvoiceHit;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.practice.internal.repository.jpa.InvoiceRepository;
import com.pravoos.ai.shared.util.ClientNameMatch;
import com.pravoos.ai.shared.util.Futures;
import com.pravoos.ai.shared.util.LikePattern;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

  private static final Logger log = LoggerFactory.getLogger(SearchService.class);
  private static final int MAX_HITS_PER_SOURCE = 10;
  private static final Pageable TOP_HITS = PageRequest.of(0, MAX_HITS_PER_SOURCE);

  private final CaseRepository caseRepository;
  private final ClientRepository clientRepository;
  private final InvoiceRepository invoiceRepository;
  private final DocumentSearchQuery documentSearchQuery;
  private final ConversationSearchQuery conversationSearchQuery;
  private final Executor globalSearchExecutor;

  public SearchService(
      CaseRepository caseRepository,
      ClientRepository clientRepository,
      InvoiceRepository invoiceRepository,
      DocumentSearchQuery documentSearchQuery,
      ConversationSearchQuery conversationSearchQuery,
      @Qualifier("globalSearchExecutor") Executor globalSearchExecutor) {
    this.caseRepository = caseRepository;
    this.clientRepository = clientRepository;
    this.invoiceRepository = invoiceRepository;
    this.documentSearchQuery = documentSearchQuery;
    this.conversationSearchQuery = conversationSearchQuery;
    this.globalSearchExecutor = globalSearchExecutor;
  }

  public GlobalSearchResponse search(UUID lawyerId, String query, boolean searchContent) {
    String trimmed = query == null ? "" : query.trim();
    if (trimmed.isEmpty()) {
      return new GlobalSearchResponse(List.of(), List.of(), List.of(), List.of(), List.of());
    }

    CompletableFuture<List<ConversationHit>> pendingConversations =
        searchAsync(() -> searchConversations(lawyerId, trimmed));
    CompletableFuture<List<DocumentHit>> pendingDocuments =
        searchAsync(() -> searchDocuments(lawyerId, trimmed, searchContent));

    List<Client> lawyerClients = clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId);
    Map<UUID, String> clientNames =
        lawyerClients.stream().collect(Collectors.toMap(Client::getId, Client::getName));
    Collection<UUID> matchingClientIds = ClientNameMatch.matchingIds(clientNames, trimmed);
    String pattern = LikePattern.contains(trimmed);

    CompletableFuture<List<CaseHit>> pendingCases =
        searchAsync(() -> searchCases(lawyerId, pattern, matchingClientIds, clientNames));
    CompletableFuture<List<InvoiceHit>> pendingInvoices =
        searchAsync(() -> searchInvoices(lawyerId, pattern, matchingClientIds, clientNames));

    List<ClientHit> clients = searchClients(lawyerClients, trimmed);
    List<CaseHit> cases = Futures.join(pendingCases);
    List<ConversationHit> conversations = Futures.join(pendingConversations);
    List<DocumentHit> documents = Futures.join(pendingDocuments);
    List<InvoiceHit> invoices = Futures.join(pendingInvoices);

    log.info(
        "Global search by lawyer {} for '{}' (content={}): {} cases, {} conversations, "
            + "{} documents, {} clients, {} invoices",
        lawyerId,
        trimmed,
        searchContent,
        cases.size(),
        conversations.size(),
        documents.size(),
        clients.size(),
        invoices.size());
    return new GlobalSearchResponse(cases, conversations, documents, clients, invoices);
  }

  private <T> CompletableFuture<T> searchAsync(Supplier<T> source) {
    return CompletableFuture.supplyAsync(source, globalSearchExecutor);
  }

  private List<CaseHit> searchCases(
      UUID lawyerId,
      String pattern,
      Collection<UUID> matchingClientIds,
      Map<UUID, String> clientNames) {
    return caseRepository.search(lawyerId, null, pattern, matchingClientIds, TOP_HITS).stream()
        .map(
            c ->
                new CaseHit(
                    c.getId(),
                    c.getTitle(),
                    c.getStatus(),
                    c.getStatus().getDisplayName(),
                    c.getClientId() == null ? null : clientNames.get(c.getClientId())))
        .toList();
  }

  private List<ConversationHit> searchConversations(UUID lawyerId, String query) {
    return conversationSearchQuery
        .searchConversations(lawyerId, query, MAX_HITS_PER_SOURCE)
        .stream()
        .map(c -> new ConversationHit(c.id(), c.title()))
        .toList();
  }

  private List<DocumentHit> searchDocuments(UUID lawyerId, String query, boolean searchContent) {
    return documentSearchQuery
        .searchDocuments(lawyerId, query, searchContent, MAX_HITS_PER_SOURCE)
        .stream()
        .map(d -> new DocumentHit(d.id(), d.title(), d.fileName(), d.caseId(), d.snippet()))
        .toList();
  }

  private List<ClientHit> searchClients(List<Client> lawyerClients, String trimmed) {
    String normalized = trimmed.toLowerCase(Locale.ROOT);
    return lawyerClients.stream()
        .filter(c -> matchesText(normalized, c.getName(), c.getEmail(), c.getPhone()))
        .limit(MAX_HITS_PER_SOURCE)
        .map(c -> new ClientHit(c.getId(), c.getName(), c.getEmail(), c.getPhone()))
        .toList();
  }

  private List<InvoiceHit> searchInvoices(
      UUID lawyerId,
      String pattern,
      Collection<UUID> matchingClientIds,
      Map<UUID, String> clientNames) {
    return invoiceRepository.search(lawyerId, pattern, matchingClientIds, TOP_HITS).stream()
        .map(
            i ->
                new InvoiceHit(
                    i.getId(),
                    i.getNumber(),
                    clientNames.get(i.getClientId()),
                    i.getTotal(),
                    i.getCurrency(),
                    i.getStatus(),
                    i.getStatus().getDisplayName()))
        .toList();
  }

  private boolean matchesText(String normalizedQuery, String... candidates) {
    for (String candidate : candidates) {
      if (candidate != null && candidate.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
        return true;
      }
    }
    return false;
  }
}
