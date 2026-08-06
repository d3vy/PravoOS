package com.pravoos.ai.document.internal;

import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.DocumentRef;
import com.pravoos.ai.document.api.DocumentResponse;
import com.pravoos.ai.document.api.DocumentSummaryView;
import com.pravoos.ai.document.api.LegislationRef;
import com.pravoos.ai.document.internal.model.entity.Document;
import com.pravoos.ai.document.internal.repository.jpa.DocumentChunkRepository;
import com.pravoos.ai.document.internal.repository.jpa.DocumentRepository;
import com.pravoos.ai.document.internal.service.DocumentService;
import com.pravoos.ai.document.internal.service.DocumentSummaryService;
import com.pravoos.ai.document.internal.service.DocumentTextExtractor;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import com.pravoos.ai.shared.model.enums.DocumentKind;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class DocumentAccessImpl implements DocumentAccess {

  private static final Logger log = LoggerFactory.getLogger(DocumentAccessImpl.class);

  private final DocumentRepository documentRepository;
  private final DocumentChunkRepository documentChunkRepository;
  private final DocumentTextExtractor documentTextExtractor;
  private final DocumentService documentService;
  private final DocumentSummaryService documentSummaryService;

  DocumentAccessImpl(
      DocumentRepository documentRepository,
      DocumentChunkRepository documentChunkRepository,
      DocumentTextExtractor documentTextExtractor,
      DocumentService documentService,
      DocumentSummaryService documentSummaryService) {
    this.documentRepository = documentRepository;
    this.documentChunkRepository = documentChunkRepository;
    this.documentTextExtractor = documentTextExtractor;
    this.documentService = documentService;
    this.documentSummaryService = documentSummaryService;
  }

  @Override
  public List<DocumentRef> findByIds(Collection<UUID> ids) {
    return documentRepository.findAllById(ids).stream().map(this::toRef).toList();
  }

  @Override
  public List<DocumentResponse> findChatAttachments(UUID lawyerId) {
    return documentService.findChatAttachments(lawyerId);
  }

  @Override
  public List<String> chunkContentsForDocuments(Collection<UUID> ids) {
    return documentChunkRepository.findContentByDocumentIdIn(new HashSet<>(ids));
  }

  @Override
  public DocumentRef findForReview(UUID id) {
    return toRef(loadOrThrow(id));
  }

  @Override
  public String extractText(UUID id) {
    return documentTextExtractor.extractText(loadOrThrow(id));
  }

  @Override
  public DocumentSummaryView summaryFor(UUID id) {
    return toSummaryView(loadOrThrow(id));
  }

  @Override
  public DocumentSummaryView regenerateSummary(UUID id, UUID requestedBy) {
    documentSummaryService.regenerate(id, requestedBy);
    return summaryFor(id);
  }

  @Override
  public boolean knowledgeBaseMentions(String needle) {
    return documentChunkRepository.existsInKnowledgeBaseByContent("%" + escapeLike(needle) + "%");
  }

  @Override
  public Optional<LegislationRef> currentLegislation(String articleNumber, String actCanonical) {
    if (articleNumber == null || articleNumber.isBlank()) {
      return Optional.empty();
    }
    String article = articleNumber.trim();
    if (actCanonical != null && !actCanonical.isBlank()) {
      return documentRepository
          .findByDocumentKindAndActCanonicalAndArticleNumberAndSupersededFalse(
              DocumentKind.LEGISLATION, actCanonical.trim(), article)
          .map(this::toLegislationRef);
    }
    List<Document> matches =
        documentRepository.findByDocumentKindAndArticleNumberAndSupersededFalse(
            DocumentKind.LEGISLATION, article);
    return matches.size() == 1 ? Optional.of(toLegislationRef(matches.get(0))) : Optional.empty();
  }

  @Override
  public Optional<LegislationRef> supersededLegislation(String articleNumber, String actCanonical) {
    if (articleNumber == null || articleNumber.isBlank()) {
      return Optional.empty();
    }
    String article = articleNumber.trim();
    if (actCanonical != null && !actCanonical.isBlank()) {
      return documentRepository
          .findFirstByDocumentKindAndActCanonicalAndArticleNumberAndSupersededTrueOrderByEditionDateDesc(
              DocumentKind.LEGISLATION, actCanonical.trim(), article)
          .map(this::toLegislationRef);
    }
    List<Document> matches =
        documentRepository
            .findByDocumentKindAndArticleNumberAndSupersededTrueOrderByEditionDateDesc(
                DocumentKind.LEGISLATION, article);
    boolean singleAct = matches.stream().map(Document::getActCanonical).distinct().count() == 1L;
    return singleAct ? Optional.of(toLegislationRef(matches.get(0))) : Optional.empty();
  }

  private LegislationRef toLegislationRef(Document document) {
    return new LegislationRef(
        document.getActCanonical(), document.getArticleNumber(), document.getEditionDate());
  }

  private Document loadOrThrow(UUID id) {
    return documentRepository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
  }

  private DocumentSummaryView toSummaryView(Document document) {
    return new DocumentSummaryView(
        document.getId(),
        document.getCaseId(),
        document.getUploadedBy(),
        document.getTitle(),
        document.getDocumentKind(),
        document.getStatus(),
        document.getSummaryStatus(),
        document.getSummary(),
        List.copyOf(document.getSummaryKeyPoints()),
        document.getSummaryGeneratedAt());
  }

  private DocumentRef toRef(Document document) {
    return new DocumentRef(
        document.getId(), document.getCaseId(), document.getUploadedBy(), document.getTitle());
  }

  private String escapeLike(String value) {
    return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }
}
