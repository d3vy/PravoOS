package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.model.dto.DocumentContent;
import com.pravoos.ai.model.dto.DocumentResponse;
import com.pravoos.ai.model.dto.DocumentUploadResponse;
import com.pravoos.ai.model.enums.AuditAction;
import com.pravoos.ai.service.AccessAuditService;
import com.pravoos.ai.practice.internal.service.PortalDocumentService;
import com.pravoos.ai.util.SecureFileHeaders;
import com.pravoos.common.web.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/portal/cases/{caseId}/documents")
public class PortalDocumentController {

    private final PortalDocumentService portalDocumentService;
    private final AccessAuditService accessAuditService;

    public PortalDocumentController(PortalDocumentService portalDocumentService,
                                    AccessAuditService accessAuditService) {
        this.portalDocumentService = portalDocumentService;
        this.accessAuditService = accessAuditService;
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> list(@PathVariable UUID caseId,
                                                       Authentication authentication) {
        return ResponseEntity.ok(portalDocumentService.listCaseDocuments(
                caseId, SecurityUtils.currentClientIds(authentication)));
    }

    @PostMapping
    public ResponseEntity<DocumentUploadResponse> upload(
            @PathVariable UUID caseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            Authentication authentication) {
        UUID clientUserId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                portalDocumentService.uploadCaseDocument(caseId, file, title, clientUserId,
                        SecurityUtils.currentClientIds(authentication)));
    }

    @GetMapping("/{documentId}/content")
    public ResponseEntity<Resource> content(@PathVariable UUID caseId,
                                            @PathVariable UUID documentId,
                                            Authentication authentication,
                                            HttpServletRequest request) {
        DocumentContent document = portalDocumentService.downloadCaseDocument(
                caseId, documentId, SecurityUtils.currentClientIds(authentication));
        accessAuditService.record(authentication, AuditAction.DOCUMENT_DOWNLOAD, documentId, request);

        boolean inline = !"docx".equals(document.fileType());
        ContentDisposition disposition = (inline
                ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(document.fileName(), StandardCharsets.UTF_8)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(disposition);
        SecureFileHeaders.apply(headers);

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(resolveMediaType(document.fileType()))
                .contentLength(document.contentLength())
                .body(document.resource());
    }

    private MediaType resolveMediaType(String fileType) {
        return switch (fileType) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "txt" -> new MediaType("text", "plain", StandardCharsets.UTF_8);
            case "docx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
