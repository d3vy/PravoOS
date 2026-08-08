package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.ConflictHit;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.repository.jpa.CasePartyRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.shared.model.enums.ConflictSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConflictCheckService {

  private static final int MIN_QUERY_LENGTH = 3;
  private static final int MAX_HITS = 20;

  private final CasePartyRepository casePartyRepository;
  private final ClientRepository clientRepository;

  public ConflictCheckService(
      CasePartyRepository casePartyRepository, ClientRepository clientRepository) {
    this.casePartyRepository = casePartyRepository;
    this.clientRepository = clientRepository;
  }

  @Transactional(readOnly = true)
  public List<ConflictHit> check(UUID lawyerId, String candidateName, UUID excludeClientId) {
    String normalizedQuery = normalize(candidateName);
    if (normalizedQuery.length() < MIN_QUERY_LENGTH) {
      return List.of();
    }

    List<ConflictHit> hits = new ArrayList<>();
    for (CasePartyRepository.PartyLookup party :
        casePartyRepository.searchConflicts(
            lawyerId, normalizedQuery, MIN_QUERY_LENGTH, MAX_HITS)) {
      hits.add(
          new ConflictHit(
              ConflictSource.CASE_PARTY,
              party.getPartyName(),
              null,
              party.getCaseId(),
              party.getCaseTitle(),
              party.getPartyRole()));
    }
    if (hits.size() >= MAX_HITS) {
      return hits;
    }

    for (Client client : clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId)) {
      if (excludeClientId != null && excludeClientId.equals(client.getId())) {
        continue;
      }
      boolean nameMatches = matches(normalizedQuery, normalize(client.getName()));
      boolean innMatches =
          client.getInn() != null && normalizedQuery.equals(normalize(client.getInn()));
      if (nameMatches || innMatches) {
        hits.add(
            new ConflictHit(
                ConflictSource.CLIENT, client.getName(), client.getId(), null, null, null));
        if (hits.size() >= MAX_HITS) {
          return hits;
        }
      }
    }

    return hits;
  }

  private boolean matches(String normalizedQuery, String normalizedCandidate) {
    if (normalizedCandidate.length() < MIN_QUERY_LENGTH) {
      return false;
    }
    return normalizedCandidate.contains(normalizedQuery)
        || normalizedQuery.contains(normalizedCandidate);
  }

  private String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }
}
