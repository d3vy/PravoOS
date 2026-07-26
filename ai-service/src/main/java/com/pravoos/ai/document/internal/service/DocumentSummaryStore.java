package com.pravoos.ai.document.internal.service;

import com.pravoos.ai.document.internal.model.entity.Document;
import com.pravoos.ai.document.internal.repository.jpa.DocumentRepository;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import com.pravoos.ai.shared.model.enums.DocumentSummaryStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentSummaryStore {

  private final DocumentRepository documentRepository;

  public DocumentSummaryStore(DocumentRepository documentRepository) {
    this.documentRepository = documentRepository;
  }

  @Transactional
  public void markPending(UUID documentId) {
    update(
        documentId,
        document -> {
          document.setSummaryStatus(DocumentSummaryStatus.PENDING);
          document.setSummaryGeneratedAt(null);
        });
  }

  @Transactional
  public void storeReady(UUID documentId, DocumentSummaryDraft draft) {
    update(
        documentId,
        document -> {
          document.setSummary(draft.summary());
          document.setSummaryKeyPoints(draft.keyPoints());
          document.setSummaryStatus(DocumentSummaryStatus.READY);
          document.setSummaryGeneratedAt(LocalDateTime.now());
        });
  }

  @Transactional
  public void markFailed(UUID documentId) {
    update(documentId, document -> document.setSummaryStatus(DocumentSummaryStatus.FAILED));
  }

  private void update(UUID documentId, Consumer<Document> mutation) {
    Document document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new DocumentNotFoundException(documentId));
    mutation.accept(document);
    documentRepository.save(document);
  }
}
