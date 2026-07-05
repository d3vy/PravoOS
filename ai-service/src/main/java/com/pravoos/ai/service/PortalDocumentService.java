package com.pravoos.ai.service;

import com.pravoos.ai.core.internal.service.DocumentService;

import com.pravoos.ai.model.dto.DocumentContent;
import com.pravoos.ai.model.dto.DocumentResponse;
import com.pravoos.ai.model.dto.DocumentUploadResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
public class PortalDocumentService {

    private final PortalCaseService portalCaseService;
    private final DocumentService documentService;

    public PortalDocumentService(PortalCaseService portalCaseService, DocumentService documentService) {
        this.portalCaseService = portalCaseService;
        this.documentService = documentService;
    }

    public List<DocumentResponse> listCaseDocuments(UUID caseId, List<UUID> clientIds) {
        portalCaseService.requireClientCase(caseId, clientIds);
        return documentService.findClientVisibleByCase(caseId);
    }

    public DocumentContent downloadCaseDocument(UUID caseId, UUID documentId, List<UUID> clientIds) {
        portalCaseService.requireClientCase(caseId, clientIds);
        return documentService.loadClientContent(documentId, caseId);
    }

    public DocumentUploadResponse uploadCaseDocument(UUID caseId, MultipartFile file, String title,
                                                     UUID clientUserId, List<UUID> clientIds) {
        portalCaseService.requireClientCase(caseId, clientIds);
        return documentService.upload(file, title, clientUserId, caseId, true);
    }
}
