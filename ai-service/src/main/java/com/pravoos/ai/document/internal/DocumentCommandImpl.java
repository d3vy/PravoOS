package com.pravoos.ai.document.internal;

import com.pravoos.ai.document.api.DocumentCommand;
import com.pravoos.ai.document.api.DocumentContent;
import com.pravoos.ai.document.api.DocumentResponse;
import com.pravoos.ai.document.api.DocumentUploadResponse;
import com.pravoos.ai.document.internal.service.DocumentService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class DocumentCommandImpl implements DocumentCommand {

    private final DocumentService documentService;

    public DocumentCommandImpl(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Override
    public DocumentUploadResponse upload(MultipartFile file, String title, UUID uploadedBy, UUID caseId) {
        return documentService.upload(file, title, uploadedBy, caseId);
    }

    @Override
    public DocumentUploadResponse uploadChatAttachment(MultipartFile file, String title, UUID lawyerId) {
        return documentService.uploadChatAttachment(file, title, lawyerId);
    }

    @Override
    public DocumentUploadResponse upload(MultipartFile file, String title, UUID uploadedBy, UUID caseId,
                                         boolean visibleToClient) {
        return documentService.upload(file, title, uploadedBy, caseId, visibleToClient);
    }

    @Override
    public void deleteByCase(UUID caseId) {
        documentService.deleteByCase(caseId);
    }

    @Override
    public DocumentResponse setClientVisibility(UUID documentId, UUID caseId, boolean visibleToClient) {
        return documentService.setClientVisibility(documentId, caseId, visibleToClient);
    }

    @Override
    public DocumentContent loadClientContent(UUID documentId, UUID caseId) {
        return documentService.loadClientContent(documentId, caseId);
    }
}
