package com.pravoos.ai.document.internal;

import com.pravoos.ai.document.api.DocumentQuery;
import com.pravoos.ai.document.internal.service.DocumentService;
import com.pravoos.ai.document.api.DocumentResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

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
