package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.core.api.ConversationSearchQuery;
import com.pravoos.ai.document.api.DocumentSearchQuery;
import com.pravoos.ai.practice.internal.dto.GlobalSearchResponse;
import com.pravoos.ai.practice.internal.dto.GlobalSearchResponse.CaseHit;
import com.pravoos.ai.practice.internal.dto.GlobalSearchResponse.ConversationHit;
import com.pravoos.ai.practice.internal.dto.GlobalSearchResponse.DocumentHit;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.shared.util.ClientNameMatch;
import com.pravoos.ai.shared.util.LikePattern;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchService {

  private static final Logger log = LoggerFactory.getLogger(SearchService.class);
  private static final int MAX_HITS_PER_SOURCE = 10;

  private final CaseRepository caseRepository;
  private final ClientRepository clientRepository;
  private final DocumentSearchQuery documentSearchQuery;
  private final ConversationSearchQuery conversationSearchQuery;

  public SearchService(
      CaseRepository caseRepository,
      ClientRepository clientRepository,
      DocumentSearchQuery documentSearchQuery,
      ConversationSearchQuery conversationSearchQuery) {
    this.caseRepository = caseRepository;
    this.clientRepository = clientRepository;
    this.documentSearchQuery = documentSearchQuery;
    this.conversationSearchQuery = conversationSearchQuery;
  }

  @Transactional(readOnly = true)
  public GlobalSearchResponse search(UUID lawyerId, String query, boolean searchContent) {
    String trimmed = query == null ? "" : query.trim();
    if (trimmed.isEmpty()) {
      return new GlobalSearchResponse(List.of(), List.of(), List.of());
    }

    List<CaseHit> cases = searchCases(lawyerId, trimmed);
    List<ConversationHit> conversations = searchConversations(lawyerId, trimmed);
    List<DocumentHit> documents = searchDocuments(lawyerId, trimmed, searchContent);

    log.info(
        "Global search by lawyer {} for '{}' (content={}): {} cases, {} conversations, {} documents",
        lawyerId,
        trimmed,
        searchContent,
        cases.size(),
        conversations.size(),
        documents.size());
    return new GlobalSearchResponse(cases, conversations, documents);
  }

  private List<CaseHit> searchCases(UUID lawyerId, String trimmed) {
    Map<UUID, String> clientNames = clientNamesFor(lawyerId);
    Collection<UUID> matchingClientIds = ClientNameMatch.matchingIds(clientNames, trimmed);
    return caseRepository
        .search(lawyerId, null, LikePattern.contains(trimmed), matchingClientIds)
        .stream()
        .limit(MAX_HITS_PER_SOURCE)
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

  private Map<UUID, String> clientNamesFor(UUID lawyerId) {
    return clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId).stream()
        .collect(Collectors.toMap(Client::getId, Client::getName));
  }
}
