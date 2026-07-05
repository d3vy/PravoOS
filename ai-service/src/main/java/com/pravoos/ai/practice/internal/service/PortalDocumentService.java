package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.core.api.DocumentCommand;
import com.pravoos.ai.core.api.DocumentContent;
import com.pravoos.ai.core.api.DocumentQuery;
import com.pravoos.ai.core.api.DocumentResponse;
import com.pravoos.ai.core.api.DocumentUploadResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
public class PortalDocumentService {

    private final PortalCaseService portalCaseService;
    private final DocumentCommand documentCommand;
    private final DocumentQuery documentQuery;

    public PortalDocumentService(PortalCaseService portalCaseService,
                                 DocumentCommand documentCommand,
                                 DocumentQuery documentQuery) {
        this.portalCaseService = portalCaseService;
        this.documentCommand = documentCommand;
        this.documentQuery = documentQuery;
    }

    public List<DocumentResponse> listCaseDocuments(UUID caseId, List<UUID> clientIds) {
        portalCaseService.requireClientCase(caseId, clientIds);
        return documentQuery.findClientVisibleByCase(caseId);
    }

    public DocumentContent downloadCaseDocument(UUID caseId, UUID documentId, List<UUID> clientIds) {
        portalCaseService.requireClientCase(caseId, clientIds);
        return documentCommand.loadClientContent(documentId, caseId);
    }

    public DocumentUploadResponse uploadCaseDocument(UUID caseId, MultipartFile file, String title,
                                                     UUID clientUserId, List<UUID> clientIds) {
        portalCaseService.requireClientCase(caseId, clientIds);
        return documentCommand.upload(file, title, clientUserId, caseId, true);
    }
}
