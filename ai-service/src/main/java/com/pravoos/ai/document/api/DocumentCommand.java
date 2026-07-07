package com.pravoos.ai.document.api;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface DocumentCommand {

    DocumentUploadResponse upload(MultipartFile file, String title, UUID uploadedBy, UUID caseId);

    DocumentUploadResponse upload(MultipartFile file, String title, UUID uploadedBy, UUID caseId,
                                  boolean visibleToClient);

    void deleteByCase(UUID caseId);

    DocumentResponse setClientVisibility(UUID documentId, UUID caseId, boolean visibleToClient);

    DocumentContent loadClientContent(UUID documentId, UUID caseId);
}
