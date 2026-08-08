package com.pravoos.ai.document.internal.service;

import com.pravoos.ai.document.internal.event.DocumentCreatedSpringEvent;
import com.pravoos.ai.document.internal.model.entity.Document;
import com.pravoos.ai.document.internal.repository.jpa.DocumentRepository;
import com.pravoos.ai.shared.model.enums.DocumentKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class DocumentUploadWriter {

  private static final Logger log = LoggerFactory.getLogger(DocumentUploadWriter.class);

  private final DocumentRepository documentRepository;
  private final ApplicationEventPublisher eventPublisher;

  DocumentUploadWriter(
      DocumentRepository documentRepository, ApplicationEventPublisher eventPublisher) {
    this.documentRepository = documentRepository;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  Document persist(Document document) {
    return saveAndPublish(document);
  }

  @Transactional
  Document persistLegislation(Document document, String act, String article) {
    documentRepository
        .findByDocumentKindAndActCanonicalAndArticleNumberAndSupersededFalse(
            DocumentKind.LEGISLATION, act, article)
        .ifPresent(
            current -> {
              current.setSuperseded(true);
              documentRepository.saveAndFlush(current);
              log.info("Legislation superseded: {} {} (doc {})", act, article, current.getId());
            });
    return saveAndPublish(document);
  }

  private Document saveAndPublish(Document document) {
    Document saved = documentRepository.save(document);
    eventPublisher.publishEvent(new DocumentCreatedSpringEvent(saved.getId()));
    return saved;
  }
}
