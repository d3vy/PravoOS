package com.pravoos.ai.document.internal;

import com.pravoos.ai.document.api.DocumentQuery;
import com.pravoos.ai.document.api.DocumentResponse;
import com.pravoos.ai.document.internal.service.DocumentService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DocumentQueryImpl implements DocumentQuery {

  private final DocumentService documentService;

  public DocumentQueryImpl(DocumentService documentService) {
    this.documentService = documentService;
  }

  @Override
  public List<DocumentResponse> findByCase(UUID caseId) {
    return documentService.findByCase(caseId);
  }

  @Override
  public List<DocumentResponse> findClientVisibleByCase(UUID caseId) {
    return documentService.findClientVisibleByCase(caseId);
  }
}
