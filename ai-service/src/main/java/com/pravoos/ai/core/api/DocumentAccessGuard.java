package com.pravoos.ai.core.api;

import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.DocumentSummaryView;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import com.pravoos.ai.shared.model.enums.DocumentKind;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DocumentAccessGuard {

  private static final Logger log = LoggerFactory.getLogger(DocumentAccessGuard.class);

  private final DocumentAccess documentAccess;
  private final CaseAccessProvider caseAccessProvider;

  public DocumentAccessGuard(DocumentAccess documentAccess, CaseAccessProvider caseAccessProvider) {
    this.documentAccess = documentAccess;
    this.caseAccessProvider = caseAccessProvider;
  }

  public DocumentSummaryView requireVisible(UUID documentId, UUID userId, List<UUID> orgIds) {
    DocumentSummaryView document = documentAccess.summaryFor(documentId);
    if (document.caseId() != null) {
      caseAccessProvider.assertCaseVisible(document.caseId(), userId, orgIds);
      return document;
    }
    if (document.documentKind() == DocumentKind.CHAT_ATTACHMENT
        && !userId.equals(document.uploadedBy())) {
      log.warn(
          "User {} attempted to access chat attachment {} owned by another user",
          userId,
          documentId);
      throw new DocumentNotFoundException(documentId);
    }
    return document;
  }
}
