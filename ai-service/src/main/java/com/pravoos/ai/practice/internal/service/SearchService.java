package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.model.dto.GlobalSearchResponse;
import com.pravoos.ai.model.dto.GlobalSearchResponse.CaseHit;
import com.pravoos.ai.model.dto.GlobalSearchResponse.ConversationHit;
import com.pravoos.ai.model.dto.GlobalSearchResponse.DocumentHit;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.core.internal.model.entity.Document;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.core.internal.repository.jpa.DocumentChunkRepository;
import com.pravoos.ai.core.internal.repository.jpa.DocumentRepository;
import com.pravoos.ai.core.internal.repository.mongo.ConversationRepository;
import com.pravoos.ai.util.LikePattern;
import com.pravoos.ai.util.SnippetExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final int MAX_HITS_PER_SOURCE = 10;
    private static final int SNIPPET_RADIUS = 80;

    private final CaseRepository caseRepository;
    private final ClientRepository clientRepository;
    private final ConversationRepository conversationRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;

    public SearchService(CaseRepository caseRepository,
                         ClientRepository clientRepository,
                         ConversationRepository conversationRepository,
                         DocumentRepository documentRepository,
                         DocumentChunkRepository documentChunkRepository) {
        this.caseRepository = caseRepository;
        this.clientRepository = clientRepository;
        this.conversationRepository = conversationRepository;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
    }

    @Transactional(readOnly = true)
    public GlobalSearchResponse search(UUID lawyerId, String query, boolean searchContent) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return new GlobalSearchResponse(List.of(), List.of(), List.of());
        }

        String pattern = LikePattern.contains(trimmed);

        List<CaseHit> cases = searchCases(lawyerId, pattern);
        List<ConversationHit> conversations = searchConversations(lawyerId, trimmed);
        List<DocumentHit> documents = searchDocuments(lawyerId, trimmed, pattern, searchContent);

        log.info("Global search by lawyer {} for '{}' (content={}): {} cases, {} conversations, {} documents",
                lawyerId, trimmed, searchContent, cases.size(), conversations.size(), documents.size());
        return new GlobalSearchResponse(cases, conversations, documents);
    }

    private List<CaseHit> searchCases(UUID lawyerId, String pattern) {
        Map<UUID, String> clientNames = clientNamesFor(lawyerId);
        return caseRepository.search(lawyerId, null, pattern).stream()
                .limit(MAX_HITS_PER_SOURCE)
                .map(c -> new CaseHit(
                        c.getId(),
                        c.getTitle(),
                        c.getStatus(),
                        c.getStatus().getDisplayName(),
                        c.getClientId() == null ? null : clientNames.get(c.getClientId())))
                .toList();
    }

    private List<ConversationHit> searchConversations(UUID lawyerId, String query) {
        return conversationRepository
                .findTop50ByLawyerIdAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(lawyerId, query).stream()
                .limit(MAX_HITS_PER_SOURCE)
                .map(c -> new ConversationHit(c.getId(), c.getTitle()))
                .toList();
    }

    private List<DocumentHit> searchDocuments(UUID lawyerId, String query, String pattern, boolean searchContent) {
        return documentRepository
                .searchOwnedByLawyer(lawyerId, pattern, searchContent, PageRequest.of(0, MAX_HITS_PER_SOURCE)).stream()
                .map(d -> new DocumentHit(d.getId(), d.getTitle(), d.getFileName(), d.getCaseId(),
                        searchContent ? contentSnippet(d, query, pattern) : null))
                .toList();
    }

    private String contentSnippet(Document document, String query, String pattern) {
        List<String> matchingChunk = documentChunkRepository
                .findMatchingContent(document.getId(), pattern, PageRequest.of(0, 1));
        if (matchingChunk.isEmpty()) {
            return null;
        }
        return SnippetExtractor.around(matchingChunk.get(0), query, SNIPPET_RADIUS);
    }

    private Map<UUID, String> clientNamesFor(UUID lawyerId) {
        return clientRepository.findByLawyerIdOrderByCreatedAtDesc(lawyerId).stream()
                .collect(Collectors.toMap(Client::getId, Client::getName));
    }
}
