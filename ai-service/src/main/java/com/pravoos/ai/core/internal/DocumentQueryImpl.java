package com.pravoos.ai.core.internal;

import com.pravoos.ai.core.api.DocumentQuery;
import com.pravoos.ai.core.internal.service.DocumentService;
import com.pravoos.ai.model.dto.DocumentResponse;
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
