package com.pravoos.ai.document.internal;

import com.pravoos.ai.document.api.DocumentSearchQuery;
import com.pravoos.ai.document.internal.model.entity.Document;
import com.pravoos.ai.document.internal.repository.jpa.DocumentChunkRepository;
import com.pravoos.ai.document.internal.repository.jpa.DocumentRepository;
import com.pravoos.ai.shared.util.LikePattern;
import com.pravoos.ai.shared.util.SnippetExtractor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DocumentSearchQueryImpl implements DocumentSearchQuery {

    private static final int SNIPPET_RADIUS = 80;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;

    public DocumentSearchQueryImpl(DocumentRepository documentRepository,
                                   DocumentChunkRepository documentChunkRepository) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentSearchHit> searchDocuments(UUID lawyerId, String query, boolean searchContent, int limit) {
        String pattern = LikePattern.contains(query);
        return documentRepository
                .searchOwnedByLawyer(lawyerId, pattern, searchContent, PageRequest.of(0, limit)).stream()
                .map(d -> new DocumentSearchHit(d.getId(), d.getTitle(), d.getFileName(), d.getCaseId(),
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
}
